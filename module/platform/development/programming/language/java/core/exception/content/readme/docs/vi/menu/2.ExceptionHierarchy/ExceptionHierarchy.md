<a id="back-to-top"></a>

# Phân cấp ngoại lệ

## Menu
- [Throwable là gốc của hệ phân cấp](#exception-hierarchy-001)
- [Exception và RuntimeException](#exception-hierarchy-002)
- [Error và VirtualMachineError](#exception-hierarchy-003)
- [Checked exception: quy tắc bắt hoặc khai báo](#exception-hierarchy-004)
- [Unchecked exception: RuntimeException, Error và các lớp con](#exception-hierarchy-005)
- [Phân biệt quy tắc checked với khả năng phục hồi thực tế](#exception-hierarchy-006)
- [Quan hệ kiểu cha, kiểu con và lựa chọn catch](#exception-hierarchy-007)
- [Vì sao catch Exception không bắt mọi Throwable](#exception-hierarchy-008)

## <a id="exception-hierarchy-001">Throwable là gốc của hệ phân cấp</a>

<details>
<summary>Xem chi tiết</summary>

# `Throwable` — gốc của hệ phân cấp ngoại lệ trong Java

Trong Java, `java.lang.Throwable` là **lớp gốc chung của mọi đối tượng có thể được ném bằng `throw` và bắt bằng `catch`**. Hai nhánh trực tiếp của nó là `Error` và `Exception`. Hiểu vị trí của `Throwable` giúp phân biệt lỗi do ứng dụng cần xử lý, lỗi nghiêm trọng của môi trường thực thi, quy tắc kiểm tra ngoại lệ của trình biên dịch và cách Java giữ thông tin chẩn đoán.

## 1. Nhìn hệ phân cấp từ gốc

```text
java.lang.Object
└── java.lang.Throwable
    ├── java.lang.Error
    │   ├── OutOfMemoryError
    │   └── LinkageError
    │       └── NoClassDefFoundError
    └── java.lang.Exception
        ├── IOException
        ├── SQLException
        └── RuntimeException
            ├── NullPointerException
            ├── IllegalArgumentException
            └── IndexOutOfBoundsException
```

Đây là **một phần tiêu biểu** của cây kế thừa, không phải danh sách đầy đủ mọi lớp ngoại lệ. Trong Java, `Throwable` kế thừa `Object` và triển khai `Serializable`. Mỗi lớp ở phía dưới cũng là một `Throwable` theo quan hệ *is-a*: một `IllegalArgumentException` vừa là `RuntimeException`, `Exception`, vừa là `Throwable`.

| Kiểu | Đặc điểm | Cách ứng xử thông thường |
| --- | --- | --- |
| `Throwable` | Gốc chung; chứa thông điệp, nguyên nhân, stack trace và ngoại lệ bị đính kèm. | Làm nền tảng cho cơ chế ngoại lệ; hiếm khi tự tạo hoặc khai báo trực tiếp trong API ứng dụng. |
| `Error` | Biểu thị các sự cố nghiêm trọng hoặc bất thường mà ứng dụng thường không thể khắc phục cục bộ. | Thông thường để lan truyền và điều tra nguyên nhân; không bắt hàng loạt để giả vờ phục hồi. |
| `Exception` (không thuộc `RuntimeException`) | Thường đại diện tình huống có thể dự kiến và xử lý, ví dụ lỗi I/O. | Phải `catch` hoặc khai báo `throws` nếu phát sinh từ phương thức. |
| `RuntimeException` | Thường gắn với tham số không hợp lệ, trạng thái sai hoặc lỗi lập trình. | Không bị buộc `catch`/`throws`; xử lý ở ranh giới có đủ ngữ cảnh hoặc sửa nguyên nhân. |

**Checked và unchecked được quyết định bởi kiểu kế thừa, không phải mức độ nghiêm trọng hay tên lớp:**

- **Unchecked:** `RuntimeException` và các lớp con; `Error` và các lớp con.
- **Checked:** các lớp kế thừa `Throwable` còn lại, bao gồm chính `Throwable`, `Exception` và những lớp con của `Exception` nằm ngoài nhánh `RuntimeException`.

Ví dụ `IOException` là checked, còn `IllegalArgumentException` và `OutOfMemoryError` là unchecked. Việc `Error` là unchecked **không** có nghĩa có thể bỏ qua tác động thực tế của nó.

## 2. `throw`, `throws` và `catch` liên hệ thế nào với `Throwable`?

`throw` ném **một đối tượng** thuộc `Throwable` hoặc lớp con. `throws` đặt trên khai báo phương thức để mô tả **kiểu ngoại lệ** có thể truyền ra. `catch` nhận đối tượng ném ra ở vị trí xử lý tương ứng.

```java
import java.io.IOException;

class Example {
    static void readConfig(boolean missing) throws IOException {
        if (missing) {
            throw new IOException("Không tìm thấy cấu hình");
        }
    }

    public static void main(String[] args) {
        try {
            readConfig(true);
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
        }
    }
}
```

Lưu thành `Example.java` rồi chạy:

```bash
javac -encoding UTF-8 Example.java
java Example
```

Kết quả:

```text
Không tìm thấy cấu hình
```

Vì `IOException` là checked, bỏ cả phần `catch` lẫn khai báo `throws IOException` ở một phương thức gọi có thể khiến mã **không biên dịch**. Ngược lại, ném `IllegalArgumentException` không bắt buộc khai báo `throws`.

Một đối tượng `new Throwable("lỗi")` hoàn toàn có thể được tạo và ném, nhưng bản thân `Throwable` cũng là **checked**. Việc khai báo `throws Throwable` cho một API công khai thường quá rộng: nó đẩy trách nhiệm xử lý cả những kiểu lỗi mà bên gọi không nên cố gắng phục hồi.

Khi có nhiều nhánh `catch`, đặt nhánh **cụ thể trước nhánh tổng quát**. Chẳng hạn `catch (IOException ex)` phải đứng trước `catch (Exception ex)` nếu cùng xử lý một `try`; làm ngược lại khiến nhánh `IOException` không thể được truy cập và trình biên dịch báo lỗi. `catch (Throwable ex)` về mặt cú pháp có thể bắt cả `Exception` và `Error`, nhưng thường quá bao quát cho xử lý nghiệp vụ.

## 3. Một `Throwable` lưu những thông tin gì?

Một đối tượng `Throwable` không chỉ là thông báo lỗi. Nó có thông tin phục vụ truy nguyên đường đi của sự cố.

| Thành phần / API | Ý nghĩa và lưu ý |
| --- | --- |
| `getClass()` | Kiểu lỗi thực tế, ví dụ `IllegalArgumentException`. |
| `getMessage()` | Thông điệp do người tạo ngoại lệ truyền vào; có thể là `null`. |
| `getLocalizedMessage()` | Mặc định trả về `getMessage()`; lớp con có thể ghi đè để bản địa hóa. |
| `getCause()` | Nguyên nhân gốc được liên kết qua constructor hoặc `initCause()`; có thể là `null`. |
| `getStackTrace()` | Mảng `StackTraceElement` biểu diễn đường gọi được ghi nhận; phần tử đầu thường là vị trí tạo/ghi nhận lỗi. |
| `printStackTrace()` | In ngoại lệ, call stack, nguyên nhân và suppressed exceptions; mặc định ra luồng lỗi chuẩn `System.err`. |
| `getSuppressed()` | Các ngoại lệ phụ bị đính kèm, đặc biệt từ thao tác đóng tài nguyên trong `try-with-resources`. |
| `toString()` | Mặc định ghép tên lớp với thông điệp (nếu có). |
| `fillInStackTrace()` | Cập nhật thông tin stack trace tại thời điểm gọi; chủ yếu phục vụ trường hợp chuyên biệt. |

**Stack trace** ghi lại chuỗi lời gọi tại thời điểm Throwable ghi nhận stack của nó, không phải lịch sử mọi thao tác trước đó. Dòng đầu của trace thường chỉ ra nơi tạo ngoại lệ; các dòng tiếp theo cho biết ai đã gọi phương thức đó. Vị trí và số dòng có thể thay đổi khi sửa hoặc biên dịch mã, nên không nên dùng nguyên văn stack trace làm đầu ra cố định của bài kiểm thử.

`Throwable` cung cấp các constructor như `Throwable()`, `Throwable(String message)`, `Throwable(String message, Throwable cause)` và `Throwable(Throwable cause)`. Nó còn có constructor `protected` cho phép cấu hình việc thu thập suppressed exceptions và stack trace; chủ yếu dành cho lớp con có nhu cầu đặc biệt.

Lỗi phổ biến là chỉ lưu `ex.getMessage()` rồi ném lỗi mới, làm mất **kiểu gốc, stack trace và cause**. Nên giữ quan hệ nguyên nhân khi chuyển lớp biểu diễn lỗi.

## 4. Ví dụ chạy được: kiểm tra quan hệ kế thừa và nguyên nhân

Tệp `ThrowableDemo.java`:

```java
public class ThrowableDemo {
    public static void main(String[] args) {
        System.out.println("Exception là Throwable: "
                + Throwable.class.isAssignableFrom(Exception.class));
        System.out.println("Error là Throwable: "
                + Throwable.class.isAssignableFrom(Error.class));
        System.out.println("IllegalArgumentException là RuntimeException: "
                + RuntimeException.class.isAssignableFrom(IllegalArgumentException.class));

        try {
            parsePort("abc");
        } catch (IllegalArgumentException ex) {
            System.out.println("Kiểu: " + ex.getClass().getSimpleName());
            System.out.println("Thông điệp: " + ex.getMessage());
            System.out.println("Nguyên nhân: " + ex.getCause().getClass().getSimpleName());
            System.out.println("Phương thức tại frame đầu: "
                    + ex.getStackTrace()[0].getMethodName());
        }
    }

    static int parsePort(String text) {
        final int port;
        try {
            port = Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Port phải là số nguyên: " + text, ex);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port phải nằm trong 1..65535");
        }
        return port;
    }
}
```

Biên dịch và chạy bằng Java 17 trở lên:

```bash
javac -encoding UTF-8 ThrowableDemo.java
java ThrowableDemo
```

Kết quả:

```text
Exception là Throwable: true
Error là Throwable: true
IllegalArgumentException là RuntimeException: true
Kiểu: IllegalArgumentException
Thông điệp: Port phải là số nguyên: abc
Nguyên nhân: NumberFormatException
Phương thức tại frame đầu: parsePort
```

Điều đáng chú ý: `Integer.parseInt("abc")` ném `NumberFormatException`. Hàm `parsePort` cung cấp thông điệp có ngữ cảnh ứng dụng bằng `IllegalArgumentException` **và giữ ngoại lệ ban đầu làm cause**. Khi điều tra lỗi, vẫn có thể lần về thao tác phân tích số. Việc kiểm tra khoảng cổng được đặt ngoài `try` bắt `NumberFormatException` để tránh vô tình gói sai lỗi kiểm tra phạm vi.

`Class.isAssignableFrom(...)` là cách kiểm tra quan hệ kiểu ở thời điểm chạy: `A.class.isAssignableFrom(B.class)` trả `true` nếu giá trị thuộc kiểu `B` có thể gán vào biến kiểu `A` theo quan hệ kiểu Java. Nó không tự tạo hay ném ngoại lệ.

## 5. Cause và suppressed exception là hai quan hệ khác nhau

**Cause** trả lời “lỗi này phát sinh từ lỗi nào?”. Constructor có tham số `cause` là cách thông thường để gắn chuỗi nguyên nhân:

```java
throw new IllegalStateException("Không thể nạp cấu hình", originalException);
```

Đây là đoạn minh họa nằm trong một phương thức đang có biến `originalException` kiểu `Throwable` phù hợp; không phải chương trình độc lập. `initCause(cause)` chỉ dùng khi nguyên nhân chưa được thiết lập; không thể đặt lại tùy ý, và không thể tự đặt chính ngoại lệ đó làm nguyên nhân của nó. Ưu tiên constructor nhận `cause` vì diễn đạt ý định ngay lúc tạo đối tượng.

**Suppressed exceptions** trả lời “trong lúc xử lý sự cố này còn lỗi phụ nào khác?”. Trường hợp tiêu biểu là khối `try-with-resources`: mã trong `try` ném một lỗi, và thao tác `close()` cũng ném lỗi. Java giữ lỗi từ phần thân `try` làm ngoại lệ chính, đính lỗi đóng tài nguyên vào `getSuppressed()`.

Tệp `SuppressedDemo.java`:

```java
public class SuppressedDemo {
    static class BrokenResource implements AutoCloseable {
        @Override
        public void close() {
            throw new IllegalStateException("close failed");
        }
    }

    public static void main(String[] args) {
        try (BrokenResource resource = new BrokenResource()) {
            throw new IllegalArgumentException("work failed");
        } catch (Exception ex) {
            System.out.println("Lỗi chính: " + ex.getMessage());
            System.out.println("Số lỗi phụ: " + ex.getSuppressed().length);
            System.out.println("Lỗi phụ: " + ex.getSuppressed()[0].getMessage());
        }
    }
}
```

```bash
javac -encoding UTF-8 SuppressedDemo.java
java SuppressedDemo
```

Kết quả:

```text
Lỗi chính: work failed
Số lỗi phụ: 1
Lỗi phụ: close failed
```

`getCause()` và `getSuppressed()` **không thể thay thế nhau**. Trong ví dụ trên, `getCause()` của lỗi chính là `null`, còn `getSuppressed()` chứa lỗi của `close()`. `Throwable.addSuppressed(...)` có thể dùng để đính kèm thủ công khi thực sự cần; phương thức từ chối tự suppress chính đối tượng và tham số `null`.

## 6. Có nên bắt `Throwable`?

Xét khối sau:

```java
try {
    performWork();
} catch (Throwable ex) {
    System.out.println("Có lỗi: " + ex.getMessage());
}
```

Đây là **đoạn minh họa**, giả định `performWork()` đã được định nghĩa. Nó sẽ bắt cả `IOException`, `RuntimeException` lẫn `OutOfMemoryError` nếu những lỗi đó được ném trong khối `try`. Việc in một dòng rồi chạy tiếp không bảo đảm trạng thái ứng dụng còn hợp lệ. Thực tế, cách này có thể che dấu lỗi nghiêm trọng; với `InterruptedException` (một checked `Exception`) thì bắt quá rộng và không xử lý đúng cũng có thể phá vỡ cơ chế hủy tác vụ.

Lựa chọn hợp lý hơn:

1. Bắt **kiểu cụ thể có thể xử lý được** ngay tại nơi có ngữ cảnh, ví dụ `IOException` khi quyết định có thử lại thao tác I/O hay không.
2. Nếu không thể phục hồi ở tầng hiện tại, cho lỗi lan truyền hoặc **bọc lỗi với cause** để bổ sung ý nghĩa nghiệp vụ.
3. Đặt xử lý lỗi tập trung ở ranh giới ứng dụng (ví dụ tầng API hoặc vòng lặp tác vụ) bằng cơ chế phù hợp; việc ghi log không đồng nghĩa đã khắc phục được lỗi.
4. Thường để `Error` lan truyền. Chỉ bắt loại `Error` cụ thể khi có lý do kỹ thuật rõ ràng và biết chắc cách xử lý an toàn; `Error` không phải mọi trường hợp đều có cùng mức nghiêm trọng.
5. Không dùng `throws Throwable` trong hợp đồng API thông thường nếu có thể khai báo kiểu checked cụ thể hơn.

`catch (Exception ex)` cũng không phải cơ chế bắt toàn bộ: nó không bắt những lỗi chỉ thuộc nhánh `Error`. Trong các đoạn mã đòi hỏi thao tác dọn dẹp bất kể kết quả, `finally` hoặc `try-with-resources` thường thích hợp hơn so với cố bắt `Throwable` chỉ để chạy logic dọn dẹp.

## 7. Ghi log, diễn giải stack trace và các lỗi thường gặp

- **Giữ nguyên đối tượng ngoại lệ khi log:** một logger hỗ trợ tham số exception sẽ ghi cả stack trace và chuỗi cause. Chỉ ghi `getMessage()` có thể làm mất dữ kiện điều tra.
- **Không lạm dụng `printStackTrace()` trong ứng dụng triển khai thực tế:** hàm này in trực tiếp ra stream, thường thiếu ngữ cảnh và cách quản lý log. Với ví dụ học tập nhỏ, nó vẫn hữu ích để quan sát cấu trúc `Throwable`.
- **Đừng giả định `getMessage()`, `getCause()` hay `getStackTrace()[0]` luôn tồn tại:** message/cause có thể `null`; stack trace có thể rỗng nếu đối tượng được cấu hình không ghi trace hoặc trace bị điều chỉnh.
- **Tránh `throw ex;` sau khi chỉ log mà không có mục đích rõ ràng:** khi bắt rồi ném lại, thông tin stack gốc của cùng đối tượng vẫn được giữ, nhưng nếu nhiều tầng cùng log, một lỗi có thể xuất hiện lặp lại. Quyết định tầng nào chịu trách nhiệm ghi log.
- **Không thay ngoại lệ bằng `new RuntimeException(ex.getMessage())`:** câu lệnh này đánh mất liên kết cause. Nếu phải bọc, dùng `new RuntimeException("Ngữ cảnh", ex)`.
- **Không tùy tiện gọi `fillInStackTrace()` hoặc `setStackTrace(...)`:** chúng thay đổi thông tin chẩn đoán. Việc tạo/ghi stack trace cũng có chi phí; tối ưu đặc biệt cho hệ thống tải cao cần có bằng chứng đo lường trước khi đánh đổi khả năng gỡ lỗi.

## 8. Tự kiểm tra hiểu bài

**Câu 1.** `throw new String("lỗi");` có hợp lệ không? **Không.** `String` không kế thừa `Throwable`; chỉ đối tượng kiểu `Throwable` hoặc lớp con mới có thể được ném.

**Câu 2.** `RuntimeException` nằm ngoài `Exception`? **Không.** Nó là lớp con trực tiếp của `Exception`; quy tắc unchecked là một quy tắc ngôn ngữ dựa trên nhánh kế thừa.

**Câu 3.** `catch (Exception ex)` bắt mọi `Error`? **Không.** `Error` và `Exception` là hai nhánh riêng dưới `Throwable`.

**Câu 4.** `Throwable` bản thân có phải checked exception? **Có.** Nó không thuộc nhánh `RuntimeException` hoặc `Error`.

**Câu 5.** Lỗi đóng tài nguyên trong `try-with-resources` có luôn thay lỗi đã xảy ra trong thân `try`? **Không.** Nếu cả hai nơi đều ném lỗi, lỗi từ thân `try` thường là lỗi chính, lỗi đóng tài nguyên được giữ ở `getSuppressed()`.

**Ghi nhớ:** `Throwable` định nghĩa nền tảng cho cơ chế ném, bắt và truy nguyên ngoại lệ của Java. Nhánh kế thừa quyết định quy tắc checked/unchecked; `message` giải thích sự cố, `stack trace` chỉ ra đường gọi, `cause` lưu chuỗi nguyên nhân và `suppressed` lưu lỗi phụ. Khi viết chương trình, hãy bắt đúng kiểu có khả năng xử lý và giữ đủ thông tin khi truyền lỗi giữa các tầng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-hierarchy-002">Exception và RuntimeException</a>

<details>
<summary>Xem chi tiết</summary>

# Exception và RuntimeException trong Java

`Exception` và `RuntimeException` đều thuộc hệ thống ngoại lệ của Java, nhưng chúng khác nhau ở **quy tắc kiểm tra của trình biên dịch** và cách khai báo, xử lý khi một phương thức có thể thất bại. Hiểu đúng mối quan hệ kế thừa giúp bạn đọc chữ ký API, đặt `try/catch` đúng chỗ và chọn loại ngoại lệ phù hợp khi thiết kế mã Java.

## 1. Vị trí trong phân cấp `Throwable`

~~~text
java.lang.Object
└── java.lang.Throwable
    ├── java.lang.Error
    └── java.lang.Exception
        ├── java.io.IOException
        ├── java.text.ParseException
        └── java.lang.RuntimeException
            ├── java.lang.IllegalArgumentException
            │   └── java.lang.NumberFormatException
            ├── java.lang.IllegalStateException
            ├── java.lang.IndexOutOfBoundsException
            └── java.lang.NullPointerException
~~~

- `Throwable` là kiểu gốc của những đối tượng Java cho phép ném bằng `throw` và bắt bằng `catch`.
- `Exception` là lớp con trực tiếp của `Throwable`, biểu diễn các tình huống bất thường mà ứng dụng có thể cần xử lý hoặc chuyển tiếp.
- `RuntimeException` **kế thừa `Exception`**. Vì vậy mọi `RuntimeException` cũng là một `Exception`, nhưng không phải mọi `Exception` đều là `RuntimeException`.
- `Error` là nhánh khác của `Throwable`, không nằm dưới `Exception`. Phần lớn ứng dụng không chủ động phục hồi các lỗi hệ thống nghiêm trọng như `OutOfMemoryError`.

Sơ đồ trên chỉ thể hiện **một số lớp tiêu biểu**, không phải toàn bộ phân cấp. Hai nhánh `RuntimeException` và `Error` thuộc nhóm *unchecked*; các lớp `Exception` còn lại thuộc nhóm *checked*.

## 2. Khác biệt quan trọng nhất: checked và unchecked

| Tiêu chí | `Exception` không thuộc nhánh `RuntimeException` | `RuntimeException` và lớp con |
| --- | --- | --- |
| Phân loại | Checked exception | Unchecked exception |
| Ví dụ | `IOException`, `ParseException`, `Exception` trực tiếp | `IllegalArgumentException`, `NullPointerException`, `NumberFormatException` |
| Trình biên dịch yêu cầu gì? | Phải **bắt** hoặc **khai báo** ngoại lệ có thể phát sinh | Không bắt buộc bắt hoặc khai báo |
| Có được khai báo `throws`? | Có; thường cần nếu không xử lý bên trong | Có, nhưng tùy chọn |
| Khi chạy có tự biến mất không? | Không; lan truyền nếu chưa được xử lý | Không; cũng lan truyền nếu chưa được xử lý |
| Có bắt được bằng `catch (Exception e)`? | Có | Có, vì là lớp con của `Exception` |

Trong Java, thuật ngữ *checked* chỉ quy tắc kiểm tra **tại thời điểm biên dịch**. Nó không bảo đảm ngoại lệ là nhẹ hoặc luôn phục hồi được. Ngược lại, *unchecked* không có nghĩa ngoại lệ bị bỏ qua: nếu được ném mà không có nơi bắt phù hợp, ngoại lệ vẫn thoát ra khỏi luồng gọi hiện tại.

Lưu ý quan trọng: bản thân lớp `Exception` là **checked** khi kiểu đó xuất hiện ở vị trí cần kiểm tra, chẳng hạn `throw new Exception(...)` hoặc chữ ký `throws Exception`. Riêng nhánh `RuntimeException` (và các lớp con) được miễn quy tắc này.

## 3. Cơ chế biên dịch và lan truyền

Khi một đoạn mã gọi phương thức có thể ném checked exception, nó phải chọn một trong hai cách:

1. **Xử lý tại đây** bằng `try/catch` phù hợp.
2. **Chuyển trách nhiệm cho nơi gọi** bằng `throws` trong khai báo phương thức.

Ví dụ chữ ký sau cho biết người gọi phải cân nhắc `IOException`:

~~~java
static String loadText(java.nio.file.Path path) throws java.io.IOException {
    return java.nio.file.Files.readString(path);
}
~~~

Đây là **đoạn minh họa khai báo phương thức**, chưa phải chương trình độc lập. Nếu gọi `loadText(path)` trong một phương thức không khai báo `throws IOException` và không bắt ngoại lệ, mã sẽ không biên dịch. Lệnh `throws` **không ném** ngoại lệ; nó mô tả những checked exception có thể thoát khỏi phương thức. Lệnh `throw` mới trực tiếp ném một đối tượng ngoại lệ.

Với unchecked exception, trình biên dịch không yêu cầu `catch` hoặc `throws`:

~~~java
static void requireNonNegative(int value) {
    if (value < 0) {
        throw new IllegalArgumentException("value phải >= 0");
    }
}
~~~

Đây cũng là **đoạn phương thức minh họa**. `IllegalArgumentException` vẫn thật sự được ném khi `value < 0`. Nó lan lên các phương thức gọi cho đến khi gặp `catch` nhận được kiểu tương ứng, hoặc trở thành ngoại lệ không được xử lý của luồng đang thực thi. Ghi `throws IllegalArgumentException` vẫn hợp lệ nếu hữu ích cho tài liệu API, nhưng không bắt buộc.

Khi phương thức khai báo một **kiểu checked rộng**, ví dụ `throws Exception`, người gọi thông thường phải xử lý hoặc khai báo kiểu đó, ngay cả khi một cách triển khai cụ thể thường chỉ ném lớp con unchecked. Quy tắc biên dịch dựa trên hợp đồng kiểu mà mã nhìn thấy.

## 4. Ví dụ Java 17+ chạy độc lập

Ví dụ dưới đây đặt checked và unchecked exception cạnh nhau, đồng thời minh họa cách giữ lại nguyên nhân ban đầu khi chuyển đổi ngoại lệ.

Tạo tệp `ExceptionDemo.java`:

~~~java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

public class ExceptionDemo {
    public static void main(String[] args) {
        try {
            System.out.println("Dòng đầu: " + readFirstLine("Java\nException"));
        } catch (IOException e) {
            System.out.println("Không đọc được dòng đầu");
        }

        for (String input : new String[] {"20", "abc", "-1"}) {
            try {
                System.out.println("Tuổi hợp lệ: " + parseAge(input));
            } catch (IllegalArgumentException e) {
                System.out.println("Input '" + input + "': " + e.getMessage());
                if (e.getCause() != null) {
                    System.out.println(
                            "Nguyên nhân: " + e.getCause().getClass().getSimpleName());
                }
            }
        }
    }

    // readLine() có chữ ký throws IOException: checked exception.
    static String readFirstLine(String text) throws IOException {
        try (BufferedReader reader =
                     new BufferedReader(new StringReader(text))) {
            return reader.readLine();
        }
    }

    // IllegalArgumentException là unchecked: không bắt buộc "throws".
    static int parseAge(String input) {
        final int age;
        try {
            age = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("phải là số nguyên", e);
        }

        if (age < 0) {
            throw new IllegalArgumentException("không được âm");
        }
        return age;
    }
}
~~~

Biên dịch và chạy từ thư mục chứa tệp:

~~~bash
javac ExceptionDemo.java
java ExceptionDemo
~~~

Kết quả chuẩn:

~~~text
Dòng đầu: Java
Tuổi hợp lệ: 20
Input 'abc': phải là số nguyên
Nguyên nhân: NumberFormatException
Input '-1': không được âm
~~~

**Giải thích từng điểm:**

- `BufferedReader.readLine()` khai báo `throws IOException`. Phương thức `readFirstLine` chọn **chuyển tiếp** bằng `throws IOException`; `main` chọn **bắt** `IOException`. Dữ liệu nằm trong `StringReader` nên nhánh lỗi I/O không được kích hoạt ở lần chạy này, nhưng chữ ký API vẫn phải được tuân thủ khi biên dịch.
- `Integer.parseInt("abc")` ném `NumberFormatException`, một lớp con của `IllegalArgumentException` và `RuntimeException`. Không cần khai báo `throws` cho `parseAge`.
- Khi đổi lỗi phân tích số thành lỗi tham số có ý nghĩa ở mức nghiệp vụ, `new IllegalArgumentException("phải là số nguyên", e)` giữ ngoại lệ gốc trong `getCause()`. Nhờ đó việc chẩn đoán vẫn truy được nguyên nhân.
- Giá trị `-1` là số nguyên hợp lệ về cú pháp nhưng không hợp lệ theo quy tắc của phương thức; chương trình chủ động ném `IllegalArgumentException`. Trường hợp này không có `cause` vì không chuyển đổi từ lỗi thấp hơn.
- `catch (IllegalArgumentException e)` bắt được cả `NumberFormatException` trực tiếp lẫn `IllegalArgumentException` vì quan hệ kế thừa. Đoạn mã không nuốt ngoại lệ một cách im lặng: nó hiển thị kết quả xử lý phù hợp với ví dụ.

Cú pháp `try (resource) { ... }` là **try-with-resources**: `BufferedReader` được đóng tự động sau khi rời khối. Nó không thay thế nghĩa vụ xử lý checked exception nếu phương thức tài nguyên có thể khai báo ngoại lệ.

## 5. Chọn kiểu ngoại lệ khi viết API

Không có quy tắc tuyệt đối rằng mọi lỗi từ bên ngoài đều phải là checked, hoặc mọi lỗi lập trình đều phải là unchecked. Hãy xác định **hợp đồng của phương thức** và hành động thực tế người gọi có thể thực hiện.

| Tình huống | Lựa chọn thường gặp | Cách nghĩ |
| --- | --- | --- |
| Đọc tệp, mở luồng I/O có thể thất bại | `IOException` (checked) | Người gọi có thể thử đường dẫn khác, thông báo lỗi hoặc chuyển tiếp |
| Tham số vi phạm điều kiện phương thức | `IllegalArgumentException` (unchecked) | Người gọi cần sửa giá trị đầu vào |
| Trạng thái đối tượng không cho phép thao tác | `IllegalStateException` (unchecked) | Cần sửa thứ tự thao tác hoặc trạng thái |
| Truy cập đối tượng `null` sai kỳ vọng | `NullPointerException` (unchecked) | Thường là vấn đề hợp đồng/logic; cần xử lý nguyên nhân |
| Chuỗi không thể chuyển thành số | `NumberFormatException` (unchecked) | Xác thực dữ liệu, hiển thị thông báo hoặc chuyển thành lỗi miền phù hợp |

Khi **có hành động phục hồi cụ thể**, bắt ngoại lệ tại tầng hiểu được lỗi. Khi không thể giải quyết, hãy cho ngoại lệ truyền lên đúng hợp đồng API. Với ngoại lệ checked, chuyển tiếp qua `throws` hoặc chuyển thành loại phù hợp và **giữ cause** khi cần. Với unchecked, ưu tiên sửa nguyên nhân hay xác thực dữ liệu đầu vào thay vì bao quanh mọi lệnh bằng `try/catch`.

Một ngoại lệ checked chỉ nên được khai báo rộng như cần thiết: `throws IOException` cho người gọi biết nhiều hơn `throws Exception`. Ngược lại, nếu API chọn checked exception, lớp triển khai và bên gọi phải tuân thủ hợp đồng này; không nên tự ý đổi toàn bộ thành `RuntimeException` chỉ để loại bỏ các khai báo `throws`.

## 6. Quy tắc bắt ngoại lệ liên quan đến kế thừa

### Bắt từ loại cụ thể đến loại tổng quát

~~~java
try {
    // Gọi một thao tác có thể ném IOException hoặc RuntimeException.
} catch (java.io.IOException e) {
    // Xử lý lỗi I/O.
} catch (RuntimeException e) {
    // Xử lý lỗi unchecked phù hợp ở tầng này.
} catch (Exception e) {
    // Các Exception còn lại, nếu có.
}
~~~

Đây là **cấu trúc minh họa**, không phải chương trình độc lập. Khi áp dụng vào mã thật, các `catch` cho checked exception phải phù hợp với các ngoại lệ mà khối `try` thực sự có thể ném. Mọi nhánh bắt đặt sau phải còn có khả năng nhận kiểu khác nhánh trước.

Vì `RuntimeException extends Exception`, đặt `catch (Exception e)` **trước** `catch (RuntimeException e)` sẽ khiến nhánh sau không thể chạy và gây lỗi biên dịch. `catch (Exception e)` bắt được cả checked và unchecked exception trong nhánh `Exception`, nhưng **không** bắt được `Error`.

Có thể dùng multi-catch với các loại không có quan hệ kế thừa, chẳng hạn `catch (IOException | IllegalArgumentException e)`, nếu các kiểu checked được khai báo phù hợp trong khối `try`. Không được viết `catch (Exception | RuntimeException e)` vì hai kiểu có quan hệ cha–con.

### Bắt hay chuyển tiếp?

- **Bắt** nếu mã biết cách xử lý: đổi thông báo sang ngữ cảnh người dùng, sử dụng phương án dự phòng đã xác định, hoặc hủy một thao tác ở đúng tầng.
- **Chuyển tiếp** nếu tầng hiện tại thiếu thông tin để phục hồi. Với checked exception, khai báo `throws` đúng loại; với unchecked exception, nó có thể tự lan truyền.
- **Chuyển đổi kiểu ngoại lệ** khi cần phản ánh hợp đồng tầng cao hơn; truyền ngoại lệ cũ làm `cause` để giữ khả năng truy vết.

Không nên bắt `Exception` hay `Throwable` ở mọi nơi chỉ để chương trình tiếp tục: việc đó có thể che lỗi logic, bỏ mất trạng thái thất bại hoặc vô tình chặn xử lý của tầng cao hơn. Một số ranh giới như bộ điều phối tác vụ hoặc tầng nhận yêu cầu có thể cố ý có xử lý tổng quát, nhưng cần chính sách lỗi rõ ràng.

## 7. Những hiểu lầm thường gặp

**“`RuntimeException` không thuộc `Exception`.”** Sai. Nó kế thừa trực tiếp `Exception`; vì vậy `catch (Exception e)` có thể nhận `RuntimeException`.

**“`Exception` luôn checked.”** Cần nói chính xác: `Exception` trực tiếp và các lớp con **không nằm trong nhánh `RuntimeException`** là checked. Các ngoại lệ nằm dưới `RuntimeException` là unchecked dù vẫn có quan hệ `is-a Exception`.

**“Unchecked exception sẽ không làm hỏng chương trình.”** Sai. Không có yêu cầu biên dịch không đồng nghĩa với việc ngoại lệ được tự động xử lý. Nếu không được bắt, nó có thể kết thúc luồng đang chạy và được runtime hoặc framework báo lại theo ngữ cảnh.

**“Mọi checked exception đều có thể phục hồi, mọi runtime exception đều là lỗi lập trình.”** Không nhất thiết. `IOException` có thể gặp vấn đề không thể khắc phục tại chỗ; `NumberFormatException` cũng có thể là dữ liệu người dùng nhập sai mà ứng dụng chủ động báo lỗi thân thiện.

**“Ghi `throws` là đã xử lý ngoại lệ.”** Sai. `throws` thông báo khả năng lan truyền; `try/catch` mới tạo điểm xử lý. `throw` là lệnh phát sinh một ngoại lệ cụ thể.

**“Bắt `Exception` là bắt mọi `Throwable`.”** Sai. `Error` thuộc nhánh song song. Không nên bắt toàn bộ `Throwable` nếu không có lý do và chính sách đặc biệt.

**“Đổi thành `RuntimeException` là đủ, không cần giữ ngoại lệ gốc.”** Không tốt cho chẩn đoán. Khi bọc lỗi ở tầng thấp, ưu tiên constructor nhận `cause`, ví dụ `new IllegalStateException("Không nạp được cấu hình", e)`.

## 8. Câu hỏi tự kiểm tra

1. Tại sao `IOException` thường phải được bắt hoặc khai báo, còn `NumberFormatException` thì không? Hãy chỉ ra nhánh kế thừa quyết định quy tắc.
2. Nếu một hàm khai báo `throws Exception`, bên gọi có được bỏ cả `catch` và `throws` vì hàm hiện tại chỉ ném `IllegalArgumentException` không?
3. `catch (Exception e)` có bắt `NullPointerException` và `OutOfMemoryError` không? Giải thích bằng sơ đồ lớp.
4. Trong ví dụ `parseAge`, tại sao nên truyền `NumberFormatException` vào constructor của `IllegalArgumentException`?
5. Khi một phương thức đọc tệp không thể phục hồi tại chỗ, bạn sẽ bắt để bỏ qua lỗi hay khai báo `throws IOException` cho tầng gọi?

**Đáp án ngắn:** (1) `IOException` là checked, `NumberFormatException` nằm dưới `RuntimeException`; (2) không, hợp đồng `throws Exception` vẫn là checked; (3) bắt `NullPointerException`, không bắt `OutOfMemoryError`; (4) giữ nguyên nhân cho việc chẩn đoán; (5) chuyển tiếp `IOException` nếu tầng gọi mới đủ ngữ cảnh quyết định.

**Ghi nhớ:** Sự phân biệt cốt lõi là *checked versus unchecked* tại lúc biên dịch. `RuntimeException` vẫn là `Exception`, và cả hai loại đều có thể lan truyền khi chạy; chọn nơi xử lý theo khả năng phục hồi và hợp đồng API.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-hierarchy-003">Error và VirtualMachineError</a>

<details>
<summary>Xem chi tiết</summary>

# Error và VirtualMachineError trong Java

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Đặt `Error` và `VirtualMachineError` đúng vị trí trong cây `Throwable`.
- Phân biệt `Error` với `Exception`/`RuntimeException` và hiểu vì sao `Error` thuộc nhóm *unchecked*.
- Nhận diện các loại `VirtualMachineError` phổ biến, nguyên nhân thường gặp và phạm vi ảnh hưởng của chúng.
- Biết giới hạn của `catch`, `finally`, cơ chế báo lỗi không được bắt và lựa chọn xử lý an toàn khi tài nguyên JVM gặp sự cố.
- Phân tích một tình huống cụ thể mà không nhầm “lỗi nghiêm trọng” với “JVM chắc chắn đã dừng”.

Các lớp trong bài thuộc `java.lang`, không cần `import`.

## 2. Vị trí trong hệ phân cấp ngoại lệ

`Throwable` là kiểu gốc của những đối tượng có thể được `throw` và `catch` trong Java. Hai nhánh quan trọng của nó là `Exception` và `Error`:

```text
java.lang.Object
└── java.lang.Throwable
    ├── java.lang.Exception
    │   └── java.lang.RuntimeException
    │       └── ... (các ngoại lệ không kiểm tra khác)
    └── java.lang.Error
        ├── java.lang.AssertionError
        ├── java.lang.LinkageError
        │   ├── java.lang.NoClassDefFoundError
        │   └── java.lang.ExceptionInInitializerError
        └── java.lang.VirtualMachineError (abstract)
            ├── java.lang.InternalError
            │   └── java.util.zip.ZipError
            ├── java.lang.OutOfMemoryError
            ├── java.lang.StackOverflowError
            └── java.lang.UnknownError
```

Đây là **sơ đồ các nhánh tiêu biểu**, không phải danh sách đầy đủ tất cả lớp con của `Error`. Bốn lớp con **trực tiếp** của `VirtualMachineError` trong Java 17 là `InternalError`, `OutOfMemoryError`, `StackOverflowError` và `UnknownError`. `ZipError` là ví dụ về lớp con **gián tiếp** thông qua `InternalError`.

Quan hệ “là một” vận hành theo kế thừa:

- Mỗi `OutOfMemoryError` đều là một `VirtualMachineError`, `Error` và `Throwable`.
- Mỗi `AssertionError` đều là một `Error`, nhưng không phải `VirtualMachineError`.
- Một `IOException` thuộc nhánh `Exception`, không thuộc nhánh `Error`.

### `Error` có phải checked exception không?

Không. Trong kiểm tra ngoại lệ lúc biên dịch, `Error` và mọi lớp con được coi là *unchecked*, tương tự `RuntimeException`. Trình biên dịch không buộc phương thức khai báo `throws OutOfMemoryError` và không buộc người gọi viết `catch`.

Điều này **không có nghĩa** lỗi không thể bị bắt; `catch (Error e)` hoặc `catch (Throwable t)` vẫn khớp về mặt cú pháp và kiểu. Nó có nghĩa Java không yêu cầu lập trình viên xử lý chúng bằng cơ chế checked exception, vì đa số tình huống không thể sửa đúng ở tầng nghiệp vụ.

## 3. `Error`: ý nghĩa và ranh giới với `Exception`

Theo hợp đồng của Java API, `Error` biểu thị những vấn đề nghiêm trọng mà một ứng dụng thông thường không được kỳ vọng sẽ cố bắt để tiếp tục như không có chuyện gì xảy ra. Không phải mọi `Error` đều phát sinh từ bộ máy JVM bị hỏng.

| Nhóm | Ví dụ | Ý nghĩa thông thường | Hướng xử lý |
| --- | --- | --- | --- |
| Checked `Exception` | `IOException`, `SQLException` | Sự cố mà mã gọi thường được yêu cầu xét xử lý/khai báo | Bắt, khôi phục, trả lỗi hoặc truyền tiếp phù hợp |
| `RuntimeException` | `IllegalArgumentException`, `NullPointerException` | Lỗi sử dụng API, trạng thái hoặc bất biến của chương trình | Sửa lỗi nguồn; bắt ở ranh giới cần thiết |
| `Error` không thuộc `VirtualMachineError` | `AssertionError`, `NoClassDefFoundError` | Vi phạm giả định hoặc sự cố liên kết/nạp lớp | Sửa cấu hình, dependency hoặc lỗi chương trình |
| `VirtualMachineError` | `OutOfMemoryError`, `StackOverflowError` | JVM gặp sự cố nội tại hoặc cạn tài nguyên cần thiết | Thu thập bằng chứng, khắc phục nguyên nhân, đánh giá khả năng tiếp tục tiến trình |

**Hai điểm dễ nhầm:**

1. `NoClassDefFoundError` không phải `ClassNotFoundException`. Lỗi đầu thuộc `LinkageError → Error`; ngoại lệ sau thuộc `Exception` và là checked exception. Chúng có tình huống phát sinh và giao diện xử lý khác nhau.
2. `AssertionError` thường phát sinh từ một lệnh `assert` bị vi phạm (khi assertion được bật), hoặc do mã chủ động tạo. Nó là `Error` nhưng bản thân nó không chứng minh JVM đã cạn bộ nhớ hay gặp lỗi nội tại.

Không nên tự tạo `new Error(...)` hay `new OutOfMemoryError(...)` để mô tả dữ liệu không hợp lệ, tài khoản không tồn tại hoặc giao dịch bị từ chối. Hãy dùng kiểu ngoại lệ biểu diễn đúng lỗi nghiệp vụ/lập trình.

## 4. `VirtualMachineError` là gì?

Khai báo lớp trong Java 17:

```java
public abstract class VirtualMachineError extends Error
```

Lớp này dùng để báo JVM bị hỏng hoặc không còn tài nguyên cần thiết để tiếp tục hoạt động bình thường. Nó là **abstract**, vì vậy thông thường bạn làm việc với các lớp con cụ thể.

| Lớp con trực tiếp | Ý nghĩa | Nguồn gốc thường gặp / hướng điều tra |
| --- | --- | --- |
| `OutOfMemoryError` | JVM không cấp phát được bộ nhớ/tài nguyên theo yêu cầu | Heap đầy, giữ tham chiếu quá lâu, Metaspace hoặc giới hạn bộ nhớ/tài nguyên khác; cần xem thông điệp và môi trường |
| `StackOverflowError` | Ngăn xếp thực thi của một luồng bị tràn | Đệ quy vô hạn, đệ quy quá sâu, vòng gọi hàm lặp lại |
| `InternalError` | JVM báo lỗi nội bộ bất ngờ | Đối chiếu phiên bản JDK/JVM, native library, log JVM; có thể cần báo lỗi cho nhà cung cấp |
| `UnknownError` | JVM báo lỗi nghiêm trọng nhưng không xác định được bản chất cụ thể | Phân tích thông tin sự cố và log JVM; không phỏng đoán từ riêng tên lớp |

### 4.1. `OutOfMemoryError`

Thông điệp lỗi cung cấp gợi ý rất quan trọng, nhưng **không có một thông điệp chuẩn duy nhất cho mọi JVM**. Trên HotSpot/JDK phổ biến, có thể gặp:

- `Java heap space`: vùng heap không đáp ứng yêu cầu cấp phát.
- `GC overhead limit exceeded`: chính sách kiểm tra GC của VM nhận thấy phần lớn thời gian dành cho GC với rất ít bộ nhớ thu hồi được.
- `Metaspace`: không có đủ vùng nhớ cho metadata của lớp.
- `unable to create native thread`: không tạo được luồng hệ điều hành; có thể do giới hạn tài nguyên của tiến trình/hệ thống.

Một `OutOfMemoryError` **không mặc định là “Java heap bị đầy”**. Tùy loại cấp phát và runtime, lỗi có thể liên quan đến bộ nhớ ngoài heap, không gian địa chỉ, giới hạn container hoặc tài nguyên hệ điều hành. Thông điệp cụ thể và JVM đang dùng quyết định hướng chẩn đoán.

Một lần cấp phát quá lớn cũng có thể bị từ chối dù chương trình vẫn có thể thực hiện những công việc khác. Vì thế, nhận định “cứ `OutOfMemoryError` là cả tiến trình ngay lập tức chết” là sai. Tuy nhiên, khi lỗi liên quan đến cạn tài nguyên thật, **không được mặc định trạng thái ứng dụng còn an toàn để phục vụ tiếp**.

### 4.2. `StackOverflowError`

Mỗi luồng có vùng stack cho các lần gọi phương thức, biến cục bộ và các thông tin thực thi cần thiết. Nếu chương trình liên tục đệ quy hoặc tạo chuỗi gọi quá sâu, nhu cầu stack vượt giới hạn và JVM có thể ném `StackOverflowError`.

Ví dụ **chỉ để đọc, không chạy**:

```java
// Ví dụ minh họa sai: không có điều kiện dừng.
static void recurseForever() {
    recurseForever();
}
```

Cách xử lý đúng là tìm điều kiện dừng bị thiếu, giảm độ sâu đệ quy, hoặc đổi thuật toán sang dạng lặp/ngăn xếp dữ liệu khi phù hợp. Tăng kích thước stack chỉ có thể làm thay đổi ngưỡng gặp lỗi, không chữa đệ quy vô hạn.

Một `StackOverflowError` có thể chỉ làm hỏng công việc của **một luồng**, không bắt buộc mọi luồng và toàn bộ JVM dừng ngay. Dù vậy, tiếp tục xử lý yêu cầu thất bại như thành công sẽ che giấu lỗi thuật toán.

### 4.3. `InternalError` và `UnknownError`

Hai lớp này gợi ý vấn đề trong tầng runtime hoặc thao tác mức thấp; rất khó xác định nguyên nhân chỉ từ tên lớp. Khi gặp chúng, cần giữ thông điệp lỗi, stack trace, thông tin JDK, môi trường và các log liên quan. `InternalError` cũng có các lớp con gián tiếp chuyên biệt, chẳng hạn `java.util.zip.ZipError`; đừng khẳng định mọi trường hợp `InternalError` đều là lỗi VM “tự nhiên” mà không xem call site.

## 5. Ví dụ chạy được: kiểm tra quan hệ kế thừa

Tạo file `ErrorHierarchyDemo.java`:

```java
public class ErrorHierarchyDemo {
    public static void main(String[] args) {
        System.out.println("Error parent: "
                + Error.class.getSuperclass().getSimpleName());
        System.out.println("VirtualMachineError parent: "
                + VirtualMachineError.class.getSuperclass().getSimpleName());
        System.out.println("OutOfMemoryError is VirtualMachineError: "
                + VirtualMachineError.class.isAssignableFrom(OutOfMemoryError.class));
        System.out.println("StackOverflowError is Error: "
                + Error.class.isAssignableFrom(StackOverflowError.class));
        System.out.println("AssertionError is VirtualMachineError: "
                + VirtualMachineError.class.isAssignableFrom(AssertionError.class));
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac ErrorHierarchyDemo.java
java ErrorHierarchyDemo
```

Kết quả chuẩn:

```text
Error parent: Throwable
VirtualMachineError parent: Error
OutOfMemoryError is VirtualMachineError: true
StackOverflowError is Error: true
AssertionError is VirtualMachineError: false
```

`Class.isAssignableFrom(Other.class)` kiểm tra liệu một đối tượng của `Other` có thể gán cho kiểu bên trái hay không. Ví dụ kiểm tra cấu trúc lớp một cách an toàn: **không tạo lỗi tài nguyên thật**.

## 6. Một `Error` không được bắt ảnh hưởng gì đến luồng?

Cơ chế lan truyền ngoại lệ vẫn áp dụng cho `Error`:

1. Khi được ném, JVM tìm `catch` phù hợp từ khung gọi hiện tại rồi đi ngược lên chuỗi lời gọi.
2. `catch (Exception e)` **không bắt** các đối tượng `Error`, vì hai nhánh tách biệt.
3. `catch (Error e)`, `catch (VirtualMachineError e)` (nếu là lớp con của loại này), hoặc `catch (Throwable t)` có thể khớp.
4. Nếu một lỗi thoát ra khỏi `Thread.run()`, cơ chế xử lý lỗi không bắt của luồng (`UncaughtExceptionHandler`) có thể nhận nó. Luồng đó kết thúc, nhưng điều này **không đồng nghĩa** mọi luồng đã kết thúc hoặc JVM buộc dừng.
5. Nếu JVM bị kết thúc đột ngột hoặc không còn tài nguyên, các bước thông thường như log, hook, `finally` hoặc handler **không có bảo đảm tuyệt đối** sẽ chạy và hoàn tất.

### Ví dụ chạy được: handler nhận `AssertionError` ở một luồng

Ví dụ này cố ý dùng `AssertionError` để **minh họa việc lan truyền của `Error` mà không gây cạn tài nguyên JVM**. `AssertionError` không phải `VirtualMachineError`.

Tạo file `UncaughtErrorDemo.java`:

```java
public class UncaughtErrorDemo {
    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(
                () -> { throw new AssertionError("demo"); },
                "demo-worker"
        );

        worker.setUncaughtExceptionHandler((thread, failure) ->
                System.out.println(thread.getName() + ": "
                        + failure.getClass().getSimpleName())
        );

        worker.start();
        worker.join();
        System.out.println("main continues");
    }
}
```

```bash
javac UncaughtErrorDemo.java
java UncaughtErrorDemo
```

Kết quả:

```text
demo-worker: AssertionError
main continues
```

`join()` khiến luồng chính chờ worker kết thúc, nên thứ tự hai dòng in là xác định trong ví dụ. Trong một hệ thống thật, nếu `VirtualMachineError` phát sinh vì thiếu bộ nhớ hoặc lỗi VM, **đừng dựa vào việc handler vẫn có thể cấp phát bộ nhớ, tạo chuỗi hoặc ghi log thành công**.

## 7. Xử lý thực tế: phòng ngừa, chẩn đoán và quyết định phục hồi

### 7.1. Khi thiết kế mã Java

- **Bắt lỗi theo mục đích.** Một ứng dụng thường bắt `Exception` hoặc các kiểu ngoại lệ cụ thể tại ranh giới xử lý có khả năng phục hồi. Tránh `catch (Throwable)` bao trùm rồi trả “thành công” cho yêu cầu.
- **Không nuốt `VirtualMachineError`.** Nếu một lớp hạ tầng đặc biệt phải bắt nó để ghi nhận trạng thái hoặc dọn dẹp tối thiểu, cần ném lại và tuân theo chính sách xử lý sự cố của tiến trình. Mã bắt lỗi chưa chắc chạy an toàn nếu tài nguyên đã cạn.
- **Không retry vô hạn** sau `OutOfMemoryError`/`StackOverflowError`. Retry cùng workload thường lặp lỗi, làm nghẽn hệ thống và che nguyên nhân.
- **Không dùng `System.gc()` như bảo đảm khắc phục `OutOfMemoryError`.** Đây chỉ là lời đề nghị thu gom rác, không đảm bảo thu hồi đủ tài nguyên.
- **Dùng `finally` và try-with-resources cho dọn dẹp bình thường**, nhưng đừng coi chúng là cơ chế phục hồi chắc chắn sau sự cố JVM nghiêm trọng.

Mẫu minh họa cách “bắt để truyền tiếp”, **là mảnh phương thức có thể đặt vào một lớp Java**, không phải chương trình `main` độc lập:

```java
static void executeBoundary(Runnable task) {
    try {
        task.run();
    } catch (VirtualMachineError fatal) {
        // Nếu có cơ chế ghi nhận an toàn và đã kiểm chứng, thực hiện tối thiểu ở đây.
        // Không biến sự cố thành kết quả thành công.
        throw fatal;
    }
}
```

Nếu ranh giới không cần thực hiện thao tác đặc biệt, **không cần thêm `catch` này**: để lỗi tự lan truyền thường đơn giản và an toàn hơn. Đừng dùng mã ví dụ trên như cam kết rằng JVM sẽ tiếp tục hoạt động được.

### 7.2. Khi vận hành ứng dụng

| Triệu chứng | Bằng chứng nên xem | Hướng sửa bền vững |
| --- | --- | --- |
| `OutOfMemoryError: Java heap space` | Dung lượng heap, heap dump, tốc độ cấp phát, đối tượng bị giữ tham chiếu | Sửa leak/giữ dữ liệu quá lâu; giới hạn cache/batch; điều chỉnh bộ nhớ dựa trên đo đạc |
| `OutOfMemoryError` liên quan Metaspace/native thread | Thông điệp đầy đủ, số lớp/class loader, số luồng, giới hạn OS/container | Tìm class loader leak, thread leak hoặc giới hạn tài nguyên |
| `StackOverflowError` | Chuỗi frame lặp trong stack trace, đường đi đệ quy, input gây lỗi | Thêm điểm dừng đúng, sửa vòng gọi, giảm độ sâu hoặc thay thuật toán |
| `InternalError`/`UnknownError` | Toàn bộ thông điệp, stack trace, log VM, phiên bản/bản phân phối JDK, native dependencies | Khoanh vùng runtime/native code; nâng cấp hoặc cung cấp ca tái hiện |

Ở môi trường HotSpot, `-XX:+HeapDumpOnOutOfMemoryError` có thể cấu hình tạo heap dump khi gặp `OutOfMemoryError`; cân nhắc dung lượng đĩa, dữ liệu nhạy cảm và chính sách vận hành. Đây là tùy chọn đặc thù triển khai JVM, không phải điều Java Language Specification bảo đảm cho mọi VM.

Quy trình ứng phó phù hợp là xác định phạm vi sự cố, giữ bằng chứng đủ để chẩn đoán, ngừng hoặc cách ly workload lỗi khi cần, rồi sửa nguyên nhân. Tại hệ thống được giám sát bởi orchestrator/process supervisor, chính sách restart có thể hữu ích sau sự cố tài nguyên nghiêm trọng; **restart không thay thế sửa leak, vô hạn đệ quy hoặc giới hạn cấu hình sai**.

## 8. Các ngộ nhận phổ biến

**“`catch (Exception)` sẽ bắt mọi thứ có thể ném.”** Sai. `Throwable` bao gồm cả `Exception` lẫn `Error`; `catch (Exception)` không bắt `Error`.

**“Mọi `Error` đều do JVM bị lỗi.”** Sai. `AssertionError` có thể là lỗi giả định của ứng dụng; `NoClassDefFoundError` có thể bắt nguồn từ classpath/dependency hoặc khởi tạo lớp thất bại.

**“Có `OutOfMemoryError` thì heap luôn đầy.”** Sai. Tình huống cấp phát, Metaspace, giới hạn thread và bộ nhớ native phải được phân biệt theo thông điệp và môi trường.

**“`StackOverflowError` buộc toàn bộ JVM phải thoát.”** Sai. Lỗi có thể phát sinh trong một luồng; tuy nhiên công việc đó thất bại và nguyên nhân đệ quy cần được sửa.

**“Bắt `VirtualMachineError` rồi thử lại mãi là phục hồi.”** Sai. Việc đó không đảm bảo tài nguyên hoặc trạng thái ứng dụng được phục hồi và có thể làm sự cố nặng thêm.

**“`finally` luôn chạy dù JVM đã chết.”** Sai. `finally` tham gia vào quá trình rời khối lệnh thông thường, nhưng không thể đảm bảo thực thi khi tiến trình/VM kết thúc bất thường.

## 9. Tự kiểm tra

1. **`OutOfMemoryError` có phải checked exception?** Không. Nó kế thừa `VirtualMachineError → Error → Throwable`, thuộc unchecked.
2. **`catch (RuntimeException e)` có bắt `StackOverflowError`?** Không. `StackOverflowError` thuộc nhánh `Error`.
3. **`AssertionError` có thuộc `VirtualMachineError`?** Không. Cả hai đều thuộc `Error` nhưng ở những nhánh khác nhau.
4. **Nếu một worker thread ném `Error` không được bắt, JVM có buộc kết thúc ngay?** Không. Thông thường luồng lỗi kết thúc; việc JVM tiếp tục hay chấm dứt còn phụ thuộc các luồng và tình trạng runtime.
5. **Đọc tên lỗi hay stack trace có đủ chẩn đoán `OutOfMemoryError`?** Không luôn đủ. Cần thông điệp đầy đủ cùng dữ liệu tài nguyên và bối cảnh vận hành.

## 10. Tài liệu chuẩn

- Java SE 17 API — [`Error`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Error.html).
- Java SE 17 API — [`VirtualMachineError`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/VirtualMachineError.html).
- Java SE 17 API — [`OutOfMemoryError`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/OutOfMemoryError.html), [`StackOverflowError`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StackOverflowError.html).
- Java Language Specification 17 — [`11.2 Compile-Time Checking of Exceptions`](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.2).

Nắm được nhánh kế thừa giúp chọn `catch` đúng kiểu; hiểu nguyên nhân giúp chọn cách phục hồi đúng mức. Đối với `VirtualMachineError`, cần đặc biệt ưu tiên chẩn đoán, giữ bằng chứng và khắc phục nguồn gây cạn tài nguyên hoặc lỗi runtime.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-hierarchy-004">Checked exception: quy tắc bắt hoặc khai báo</a>

<details>
<summary>Xem chi tiết</summary>

# Checked exception trong Java: quy tắc bắt hoặc khai báo

Checked exception là ngoại lệ mà trình biên dịch Java yêu cầu mã nguồn **xử lý bằng `catch` hoặc khai báo bằng `throws`** khi một lời gọi, biểu thức hay câu lệnh có khả năng phát sinh nó. Quy tắc này thường được gọi là **catch-or-declare** (hoặc catch-or-specify). Nó giúp hợp đồng của phương thức cho người gọi biết những tình huống thất bại cần cân nhắc, chẳng hạn đọc tệp, truy cập dữ liệu hoặc chờ một luồng.

Sau bài này, bạn cần biết cách nhận diện checked exception từ cây kế thừa, giải thích lỗi biên dịch `unreported exception`, chọn đúng giữa xử lý tại chỗ và chuyển trách nhiệm lên tầng gọi, đồng thời áp dụng quy tắc cho phương thức, constructor, overriding và lambda.

## 1. Vị trí trong phân cấp ngoại lệ

~~~text
java.lang.Throwable
├── java.lang.Error                         (unchecked)
│   └── ...                                 (unchecked)
└── java.lang.Exception                     (checked)
    ├── java.io.IOException                 (checked)
    │   ├── java.io.FileNotFoundException   (checked)
    │   └── ...
    ├── java.sql.SQLException               (checked)
    ├── java.lang.InterruptedException      (checked)
    └── java.lang.RuntimeException          (unchecked)
        ├── NullPointerException            (unchecked)
        ├── IllegalArgumentException        (unchecked)
        └── ...
~~~

- **Checked:** những lớp con của `Throwable` mà **không** thuộc nhánh `RuntimeException` hay `Error`. Vì vậy chính `Exception` (ngoại trừ nhánh runtime), `IOException`, `SQLException`, `ClassNotFoundException` và `InterruptedException` đều có cơ chế kiểm tra ở thời điểm biên dịch.
- **Unchecked:** `RuntimeException`, `Error` và các lớp con của chúng. Có thể bắt hoặc khai báo, nhưng trình biên dịch không bắt buộc làm vậy.
- Cả checked và unchecked đều chỉ **thực sự phát sinh khi chương trình chạy**; sự khác biệt là Java có yêu cầu tĩnh về `catch`/`throws` đối với checked exception.

Ví dụ API: `Files.readString(path)` khai báo `IOException`; `Thread.sleep(millis)` khai báo `InterruptedException`; `Integer.parseInt(text)` có thể ném `NumberFormatException` (unchecked). Đừng suy đoán loại ngoại lệ qua tên thao tác: đọc chữ ký phương thức và Javadoc.

## 2. Quy tắc catch-or-declare hoạt động ra sao?

Giả sử một phương thức có thể ném `IOException`:

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

// Đoạn minh họa đặt bên trong một lớp.
static String read(Path file) throws IOException {
    return Files.readString(file);
}
~~~

Mọi phương thức gọi `read(file)` phải chọn một trong hai cách:

1. **Bắt (`catch`):** đặt lời gọi trong `try` và cung cấp `catch (IOException ex)` hoặc một kiểu cha phù hợp. Lúc đó trách nhiệm xử lý đã được giải quyết tại đây, trừ khi bạn chủ động ném tiếp.
2. **Khai báo (`throws`):** thêm `throws IOException` (hoặc kiểu cha thích hợp, chẳng hạn `Exception`) vào chữ ký phương thức gọi. Ngoại lệ nếu phát sinh sẽ tiếp tục truyền đến người gọi của phương thức đó; tầng tiếp theo lại phải bắt hoặc khai báo.

Hai cách có thể cùng tồn tại: một phương thức bắt `FileNotFoundException`, nhưng vẫn khai báo `IOException` cho những lỗi I/O còn lại. Compiler theo dõi **kiểu ngoại lệ có thể phát sinh theo mã nguồn**; có `throws` không có nghĩa phương thức luôn ném ngoại lệ.

### Ví dụ không hợp lệ

~~~java
// Minh họa lỗi biên dịch; không phải một chương trình Java độc lập.
static String invalidRead(Path file) {
    return Files.readString(file);
}
~~~

Vì `Files.readString` khai báo `IOException` nhưng phương thức trên không bắt và không khai báo, `javac` sẽ báo lỗi chứa thông điệp tương tự `unreported exception java.io.IOException; must be caught or declared to be thrown`. Việc gọi đến đoạn mã lỗi chưa xảy ra cũng không giúp vượt qua kiểm tra biên dịch.

## 3. Ví dụ hoàn chỉnh: chuyển lên tầng gọi rồi bắt ở ranh giới ứng dụng

Tạo tệp `CatchOrDeclareDemo.java`:

~~~java
import java.io.IOException;

public class CatchOrDeclareDemo {
    static String readConfiguration() throws IOException {
        throw new IOException("thiếu config.properties");
    }

    static String startService() throws IOException {
        return readConfiguration();
    }

    public static void main(String[] args) {
        try {
            System.out.println(startService());
        } catch (IOException ex) {
            System.out.println("Không khởi động: " + ex.getMessage());
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac CatchOrDeclareDemo.java
java CatchOrDeclareDemo
~~~

Đầu ra chính xác:

~~~text
Không khởi động: thiếu config.properties
~~~

Diễn giải luồng thực thi:

1. `readConfiguration()` thực hiện `throw new IOException(...)`. Từ khóa `throw` **ném một đối tượng ngoại lệ**; `throws IOException` ở chữ ký **khai báo khả năng ném**.
2. `startService()` không bắt nên phải khai báo `throws IOException`. Ngoại lệ đi từ `readConfiguration` qua `startService` lên `main`.
3. `main()` bắt `IOException`, chọn thông báo phù hợp và tiếp tục đến cuối chương trình. Câu lệnh in kết quả bình thường trong khối `try` bị bỏ qua khi ngoại lệ xuất hiện.

Nếu đổi `main` thành `public static void main(String[] args) throws IOException` và gọi trực tiếp `startService()`, mã vẫn **biên dịch**, nhưng vì không có tầng Java nào ở đây bắt ngoại lệ, ứng dụng sẽ kết thúc bất thường và JVM thường in stack trace. Khai báo `throws` không đồng nghĩa với việc lỗi đã được phục hồi.

Trong thực tế, `readConfiguration()` có thể dùng `Files.readString(Path.of("config.properties"))` thay vì ném ngoại lệ cố định. Khi dùng đường dẫn thật, kết quả thành công/thất bại sẽ phụ thuộc vào hệ thống tệp và quyền truy cập.

## 4. Chọn `catch` hay `throws`?

| Tình huống | Thường chọn | Lý do |
| --- | --- | --- |
| Lớp giao tiếp người dùng có thể yêu cầu chọn tệp khác | `catch` | Có phương án phục hồi cụ thể. |
| Hàm tiện ích đọc tệp không biết cách báo lỗi cho người dùng | `throws IOException` | Giữ nguyên thông tin thất bại cho tầng quyết định. |
| Tầng dịch vụ cần bổ sung ngữ cảnh nghiệp vụ | Bắt rồi ném ngoại lệ phù hợp, kèm `cause` | Người gọi hiểu đang thất bại ở thao tác nào. |
| Luồng bị ngắt khi chờ | Khai báo `InterruptedException` hoặc bắt và khôi phục cờ ngắt | Tôn trọng yêu cầu hủy/ngắt công việc. |
| Lỗi lập trình như đối số sai, truy cập `null` | Sửa nguyên nhân; unchecked nếu cần biểu diễn vi phạm hợp đồng | Không cần ép quy trình catch-or-declare. |

Nguyên tắc: **bắt ở nơi có thể quyết định phải làm gì với lỗi**; nếu chưa thể xử lý hợp lý, thường nên khai báo hoặc chuyển thành ngoại lệ có ngữ cảnh và giữ nguyên nguyên nhân. Tránh `catch (Exception ex) { }` vì nó nuốt lỗi, khiến thao tác phía sau tưởng rằng mọi việc thành công. Cũng tránh chỉ `printStackTrace()` rồi trả về dữ liệu giả như thể đọc thành công.

`throws Exception` và `catch (Exception ex)` hợp lệ về mặt ngôn ngữ, nhưng làm mờ loại lỗi mà API muốn công bố và dễ bắt cả lỗi lập trình không mong đợi. Ưu tiên loại checked exception đủ cụ thể, trừ khi API thực sự có hợp đồng rộng.

## 5. Những quy tắc quan trọng về phạm vi và kiểu

### Kiểu cha, kiểu con và nhiều ngoại lệ

- `catch (IOException ex)` cũng xử lý các lớp con như `FileNotFoundException`, `NoSuchFileException` khi chúng phát sinh.
- Khi có nhiều `catch`, đặt kiểu **cụ thể trước, tổng quát sau**. Nếu đặt `catch (IOException)` trước `catch (FileNotFoundException)`, nhánh phía sau không thể được chọn và trình biên dịch báo lỗi.
- Một phương thức có thể khai báo `throws IOException, SQLException`. Người gọi phải bao phủ cả hai bằng các `catch` thích hợp hoặc tiếp tục khai báo.
- Có thể dùng multi-catch `catch (IOException | SQLException ex)` khi các kiểu là những lựa chọn không có quan hệ kế thừa với nhau. Không viết `IOException | FileNotFoundException`: kiểu thứ hai là con của kiểu thứ nhất.
- `catch` không tự động bắt mọi `Throwable`: chỉ các đối tượng tương thích với kiểu đã khai báo mới khớp; một ngoại lệ chưa được bắt sẽ truyền ra ngoài.

Ví dụ bắt cụ thể trước tổng quát (đoạn minh họa đặt trong phương thức có biến `Path file`):

~~~java
try {
    String content = Files.readString(file);
    System.out.println(content);
} catch (java.nio.file.NoSuchFileException ex) {
    System.out.println("Không tìm thấy tệp được yêu cầu.");
} catch (IOException ex) {
    System.out.println("Không thể đọc tệp: " + ex.getMessage());
}
~~~

Trường hợp `IOException` không thể phát sinh từ khối `try` theo phân tích của compiler mà bạn vẫn viết `catch (IOException)`, Java có thể báo lỗi kiểu `exception IOException is never thrown in body of corresponding try statement`. Vì vậy không thêm `catch` chỉ để “cho đủ”, đặc biệt với checked exception.

### `throws` và hợp đồng của phương thức

Đối với checked exception, lời khai báo trong một phương thức/constructor là phần của **hợp đồng API**: những nơi gọi nó phải chuẩn bị xử lý. `throws IOException` cho phép khai báo kiểu cha của `FileNotFoundException` vì người gọi bắt `IOException` cũng bao trùm kiểu con.

Một phương thức có thể khai báo checked exception dù nhánh thực thi hiện tại không thật sự ném nó. Tuy nhiên, đừng khai báo rộng hoặc không liên quan nếu không có lý do về hợp đồng. Trong thiết kế API, loại checked exception cụ thể giúp người dùng xác định cách phục hồi.

### Ghi đè (`override`) và triển khai interface

Phương thức ghi đè **không được thêm checked exception mới hoặc rộng hơn** so với phương thức bị ghi đè; nó có thể không khai báo, hoặc khai báo loại checked hẹp hơn được hợp đồng cha cho phép. Ví dụ sau là hợp lệ:

~~~java
import java.io.FileNotFoundException;
import java.io.IOException;

interface Repository {
    String load() throws IOException;
}

class TextRepository implements Repository {
    @Override
    public String load() throws FileNotFoundException {
        return "demo";
    }
}
~~~

Nếu `TextRepository.load()` khai báo `throws Exception` hoặc `throws SQLException`, mã không hợp lệ vì hợp đồng `Repository.load()` chỉ cho phép `IOException` và các kiểu con của nó. Ngoại lệ unchecked không chịu giới hạn `throws` này. Khi một lớp triển khai nhiều interface có cùng chữ ký, phải thỏa mãn tất cả hợp đồng liên quan.

### Constructor, initializer và lambda

- Constructor chịu nguyên tắc bắt/khai báo giống phương thức. Nếu lời gọi `new FileInputStream(path)` có thể ném `FileNotFoundException`, constructor chứa lời gọi cũng phải xử lý hoặc khai báo.
- Mã khởi tạo đối tượng/khối khởi tạo có quy tắc riêng theo ngữ cảnh; không thể tùy ý ném checked exception ra khỏi static initializer bằng một `throws` gắn lên khối. Đối với instance initializer, phải xét các constructor liên quan.
- Lambda và method reference cũng được compiler kiểm tra theo hợp đồng `throws` của phương thức trừu tượng mà functional interface khai báo. `Runnable.run()` không khai báo checked exception, nên không thể trực tiếp ném `IOException` từ lambda gán cho `Runnable`. Ngược lại, `Callable.call()` khai báo `throws Exception`, vì vậy có thể viết:

~~~java
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

// Đoạn minh họa trong một phương thức.
Callable<String> task = () -> Files.readString(Path.of("config.properties"));
~~~

Lambda trên chỉ **tạo tác vụ**, chưa đọc tệp tại thời điểm khai báo. Khi tác vụ được gọi, `Callable.call()` có thể ném checked exception. Tương tự, các hàm trong Stream như `Function.apply` không khai báo checked exception, nên `stream.map(x -> Files.readString(x))` không biên dịch nếu không có xử lý phù hợp.

## 6. Kết hợp try-with-resources với checked exception

Một thao tác I/O có thể lỗi lúc **mở**, **đọc** hoặc **đóng** tài nguyên. `try-with-resources` đảm bảo gọi `close()` khi rời khối `try`, kể cả khi đang xử lý ngoại lệ.

~~~java
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

// Đoạn minh họa đặt trong một lớp.
static String firstLine(Path path) throws IOException {
    try (BufferedReader reader = Files.newBufferedReader(path)) {
        return reader.readLine();
    }
}
~~~

`Files.newBufferedReader`, `reader.readLine` và `reader.close` có thể tạo ra `IOException`; `throws IOException` ở `firstLine` bao phủ trách nhiệm của cả thao tác đọc lẫn đóng. Nếu khối xử lý và lệnh đóng cùng ném lỗi, lỗi từ đóng thường được gắn dưới dạng **suppressed exception** của lỗi chính trong cơ chế try-with-resources; kiểm tra bằng `getSuppressed()` khi cần chẩn đoán. Đừng tự đóng tài nguyên trong `finally` một cách cẩu thả, vì lỗi khi đóng có thể che mất lỗi gốc.

`finally` hữu ích cho dọn dẹp logic không dựa trên `AutoCloseable`, nhưng không thay thế được quy tắc bắt/khai báo checked exception. Hơn nữa, `return` hoặc `throw` trong `finally` có thể che khuất kết quả/ngoại lệ phát sinh trước đó, nên cần tránh.

## 7. Chuyển đổi và bảo toàn nguyên nhân của ngoại lệ

Đôi khi một tầng cần công bố lỗi ở mức khái niệm khác. Bạn có thể tạo một checked exception riêng bằng cách kế thừa `Exception`, rồi truyền ngoại lệ gốc làm `cause`:

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class ConfigurationException extends Exception {
    ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}

class ConfigurationLoader {
    static String load(Path path) throws ConfigurationException {
        try {
            return Files.readString(path);
        } catch (IOException ex) {
            throw new ConfigurationException("Không đọc được cấu hình: " + path, ex);
        }
    }
}
~~~

Ở đây `ConfigurationLoader.load` **không cần khai báo `IOException`** vì đã bắt nó; thay vào đó, nó phải khai báo `ConfigurationException` mới được ném. Người gọi `load` lại có trách nhiệm bắt hoặc khai báo ngoại lệ checked mới. `getCause()` giữ `IOException` gốc để phục vụ chẩn đoán.

Nếu bắt buộc thích ứng với API không cho khai báo checked exception (ví dụ một functional interface), có thể chuyển một `IOException` thành `UncheckedIOException` với `cause` đầy đủ, nhưng đó là lựa chọn thiết kế có chủ đích; người gọi lúc này không còn được compiler buộc xử lý. Đừng âm thầm nuốt lỗi hoặc biến tất cả ngoại lệ thành `RuntimeException` chỉ để mã biên dịch.

## 8. Trường hợp đặc biệt: InterruptedException

`Thread.sleep`, `Object.wait` và nhiều thao tác chặn khác có thể ném `InterruptedException` (checked). Cách ưu tiên ở tầng có thể chuyển tiếp là khai báo `throws InterruptedException` để người gọi quyết định hủy tác vụ.

Nếu một phương thức không thể khai báo và phải bắt ngoại lệ này, thông thường cần **khôi phục trạng thái ngắt** để mã ở tầng trên nhận ra tín hiệu:

~~~java
// Đoạn minh họa là phương thức bên trong một lớp.
static void pauseBriefly() {
    try {
        Thread.sleep(100);
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return;
    }
}
~~~

`Thread.sleep` sẽ xóa cờ interrupted khi ném `InterruptedException`; gọi lại `interrupt()` phục hồi tín hiệu cho logic kiểm tra sau đó. Trong chương trình có vòng lặp tác vụ, sau khi phục hồi cờ ngắt nên kết thúc hoặc chuyển quyền xử lý, tránh tiếp tục chạy vô hạn. Không dùng lại cách này một cách máy móc với mọi `IOException` vì mục đích ngữ nghĩa khác nhau.

## 9. Lỗi thường gặp và cách sửa

| Sai sót | Hệ quả | Cách sửa |
| --- | --- | --- |
| Gọi phương thức khai báo checked exception mà không có `catch`/`throws` | Lỗi biên dịch `unreported exception` | Bắt đúng kiểu hoặc khai báo ở phương thức chứa lời gọi. |
| Nhầm `throw` và `throws` | Tưởng khai báo là đã xử lý lỗi | `throw` thực hiện ném; `throws` mô tả hợp đồng. |
| Chỉ thêm `throws Exception` ở mọi lớp | API mất thông tin cụ thể, đẩy trách nhiệm mơ hồ | Khai báo ngoại lệ cụ thể hoặc bắt ở nơi xử lý được. |
| Bắt `Exception` rồi để khối bắt rỗng | Mất tín hiệu thất bại, khó truy tìm lỗi | Phục hồi thật sự, chuyển tiếp, hoặc bọc kèm nguyên nhân. |
| Bắt lớp cha trước lớp con | `catch` phía sau không thể tới, mã không biên dịch | Sắp xếp nhánh cụ thể trước nhánh tổng quát. |
| Thêm `throws SQLException` vào override khi interface chỉ cho `IOException` | Vi phạm hợp đồng phương thức | Giữ tập checked exception cùng loại hoặc hẹp hơn cha. |
| Bắt `InterruptedException` nhưng không chuyển tiếp/khôi phục cờ ngắt | Mất tín hiệu hủy công việc | `throws InterruptedException` hoặc `Thread.currentThread().interrupt()` rồi dừng phù hợp. |
| Ném ngoại lệ mới nhưng bỏ `cause` | Mất dấu vết lỗi gốc | Dùng constructor nhận `cause` hoặc `initCause` khi thích hợp. |

## 10. Tự kiểm tra

1. `IOException` khác `NullPointerException` ở yêu cầu biên dịch nào? **Đáp án:** `IOException` là checked, nên phải được bắt hoặc khai báo khi có thể phát sinh; `NullPointerException` là unchecked, không có yêu cầu đó.
2. A khai báo `throws IOException`, B gọi A nhưng không bắt. B phải làm gì? **Đáp án:** B khai báo `throws IOException` hoặc một kiểu cha phù hợp; tiếp tục chuyển trách nhiệm cho nơi gọi B.
3. `catch (Exception ex)` có thể bắt `IOException` không? **Có**, nhưng thường quá rộng; cần cân nhắc bắt chính xác và không nuốt lỗi.
4. Trong override của phương thức cha `throws IOException`, có thể khai báo `throws FileNotFoundException` không? **Có**, vì `FileNotFoundException` là lớp con của `IOException`.
5. Dùng `throws IOException` trong `main` có đảm bảo ứng dụng không dừng khi lỗi I/O xuất hiện? **Không**; nó chỉ chuyển ngoại lệ ra khỏi `main`, không thực hiện phục hồi.

## Tài liệu đối chiếu

- Java Language Specification 17, [§11.1.1 và §11.2 — Exception Types và Compile-Time Checking of Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html).
- Oracle Java Tutorials, [The Catch or Specify Requirement](https://docs.oracle.com/javase/tutorial/essential/exceptions/catchOrDeclare.html) (trang giới thiệu cho JDK 8, quy tắc cốt lõi vẫn áp dụng).
- Oracle Java Tutorials, [The try-with-resources Statement](https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html).

**Ghi nhớ:** `throws` là một phần của hợp đồng truyền lỗi; `catch` là nơi bạn đưa ra cách xử lý. Chọn điểm xử lý theo khả năng phục hồi thực tế và luôn bảo toàn thông tin cần thiết về nguyên nhân ngoại lệ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-hierarchy-005">Unchecked exception: RuntimeException, Error và các lớp con</a>

<details>
<summary>Xem chi tiết</summary>

# Unchecked exception trong Java: `RuntimeException`, `Error` và các lớp con

Java phân chia các đối tượng có thể ném (`Throwable`) thành hai nhóm theo **quy tắc kiểm tra ngoại lệ của trình biên dịch**: *checked exception* và *unchecked exception*. **Unchecked exception** gồm `RuntimeException`, `Error` và mọi lớp con của hai loại này. Khi gọi một phương thức có thể ném unchecked exception, trình biên dịch Java **không bắt buộc** nơi gọi phải viết `try/catch` hoặc khai báo `throws` cho exception đó.

Từ “unchecked” chỉ nói về **nghĩa vụ khai báo hoặc bắt ngoại lệ tại thời điểm biên dịch**. Nó **không** có nghĩa JVM bỏ qua ngoại lệ, chương trình chắc chắn tiếp tục chạy, ngoại lệ không nghiêm trọng, hay Java không thể phát hiện một số vấn đề từ sớm. Khi một unchecked exception thực sự được ném, nó vẫn sử dụng đầy đủ cơ chế của `Throwable`: ngắt đường thực thi thông thường, đi lên chuỗi lời gọi, được tìm `catch` phù hợp, mang message, cause và stack trace.

Điểm quan trọng nhất của bài: cả hai đều unchecked nhưng **`RuntimeException` thường gắn với thao tác hoặc trạng thái ứng dụng**, còn **`Error` thường biểu diễn vấn đề nghiêm trọng ở môi trường thực thi, liên kết lớp hoặc bất biến nội bộ**. Cách ứng xử thường khác nhau và không thể chỉ dựa vào chữ “unchecked”.

## 1. Unchecked nằm ở đâu trong hệ phân cấp?

Sơ đồ rút gọn, không liệt kê toàn bộ Java API:

```text
java.lang.Throwable
├── java.lang.Error                              ← unchecked
│   ├── VirtualMachineError
│   │   ├── OutOfMemoryError
│   │   └── StackOverflowError
│   ├── LinkageError
│   │   ├── NoClassDefFoundError
│   │   └── ExceptionInInitializerError
│   └── AssertionError
└── java.lang.Exception                          ← checked (bản thân kiểu này)
    ├── IOException                              ← checked
    ├── ReflectiveOperationException             ← checked
    └── RuntimeException                         ← unchecked
        ├── IllegalArgumentException
        │   └── NumberFormatException
        ├── IllegalStateException
        ├── NullPointerException
        ├── IndexOutOfBoundsException
        │   ├── ArrayIndexOutOfBoundsException
        │   └── StringIndexOutOfBoundsException
        ├── ArithmeticException
        ├── ClassCastException
        └── UnsupportedOperationException
```

Các tên ví dụ trong sơ đồ là các lớp Java thực. Một lớp con của `RuntimeException` vẫn thuộc nhánh `Exception` nhưng **không còn là checked exception**. Tương tự, mọi lớp con của `Error` thuộc nhóm unchecked.

Theo quy tắc của ngôn ngữ Java, checked exception là các lớp con của `Throwable` **không** thuộc nhánh `RuntimeException` hoặc nhánh `Error`. Vì vậy `Exception` là một kiểu checked, trong khi `RuntimeException` là unchecked. Nói “mọi `Exception` đều checked” là sai.

| Nhánh | Checked hay unchecked? | Điều trình biên dịch yêu cầu ở nơi gọi | Phản ứng điển hình |
| --- | --- | --- | --- |
| `IOException` | Checked | Bắt hoặc khai báo nếu phương thức có thể ném | Quyết định khôi phục, dùng phương án dự phòng hoặc truyền lỗi |
| `RuntimeException` và các lớp con | Unchecked | Không bắt buộc bắt/khai báo | Kiểm tra tiền điều kiện, trạng thái, dữ liệu; bắt có chọn lọc ở tầng phù hợp |
| `Error` và các lớp con | Unchecked | Không bắt buộc bắt/khai báo | Ưu tiên điều tra/sửa môi trường hoặc chương trình; thường không xử lý như một nhánh nghiệp vụ |
| `Exception` | Checked | Bắt hoặc khai báo nếu một biểu thức có thể ném kiểu này | Không đồng nhất với riêng `RuntimeException` |

**Phân biệt thuật ngữ:** “lỗi biên dịch” là việc `javac` không chấp nhận mã nguồn; “lỗi runtime” là vấn đề khi chạy; còn lớp Java `Error` là **một nhánh cụ thể của `Throwable`**. Không phải mọi lỗi chạy đều là đối tượng `Error`. Một `NullPointerException`, chẳng hạn, là `RuntimeException`.

## 2. Vì sao `RuntimeException` không bị kiểm tra bắt buộc ở compile time?

Nhiều sai sót được phát hiện khi chạy phụ thuộc giá trị hoặc trạng thái mà trình biên dịch không thể biết từ một lời gọi: tham số có phải `null` không, chỉ số mảng có nằm trong giới hạn không, dữ liệu đầu vào có chuyển thành số được không, đối tượng có đang ở trạng thái cho phép hay không.

Ví dụ, lời gọi `Integer.parseInt(text)` hợp lệ về kiểu vì tham số là `String`. Java không thể đảm bảo rằng mỗi giá trị thực tế của `text` biểu diễn số nguyên hợp lệ. API có thể ném `NumberFormatException` (một lớp con của `IllegalArgumentException`, rồi `RuntimeException`), nhưng mọi nơi gọi `parseInt` không bị buộc phải có `catch`.

Tương tự, các phương thức Java rất thường xuyên có thể gặp lỗi như `NullPointerException`; nếu tất cả chúng đều phải khai báo hoặc bắt checked exception, chữ ký và luồng gọi sẽ trở nên rất cồng kềnh mà không làm mã an toàn hơn theo cách tương ứng.

**Không bị kiểm tra bắt buộc khác với không kiểm tra được bằng công cụ.** Compiler, IDE, phân tích tĩnh, test và công cụ kiểm tra `null` có thể phát hiện một phần nguy cơ trước khi chạy; chỉ là quy tắc `catch-or-declare` của Java không áp dụng cho các loại này.

Cũng đừng hiểu “unchecked” đồng nghĩa với bug: một số API chủ động sử dụng `IllegalArgumentException` để báo bên gọi vi phạm hợp đồng; một số ngoại lệ có thể đến từ dữ liệu bên ngoài. Tuy nhiên, nếu cùng tình huống là nhánh nghiệp vụ thường xảy ra, loại kết quả rõ ràng có thể dễ dùng hơn việc ném ngoại lệ.

## 3. Ví dụ kiểm tra chính xác nhánh nào là unchecked

Lưu đoạn mã sau thành `ExceptionHierarchyDemo.java`. Chương trình dùng quan hệ kế thừa kiểu của Java để kiểm tra loại nào thuộc nhánh `RuntimeException` hoặc `Error`.

```java
import java.io.IOException;

public class ExceptionHierarchyDemo {
    static void inspect(Class<? extends Throwable> type) {
        boolean runtime = RuntimeException.class.isAssignableFrom(type);
        boolean error = Error.class.isAssignableFrom(type);
        boolean unchecked = runtime || error;

        System.out.println(type.getSimpleName()
                + " | unchecked=" + unchecked
                + " | runtime=" + runtime
                + " | error=" + error);
    }

    public static void main(String[] args) {
        inspect(IOException.class);
        inspect(Exception.class);
        inspect(NumberFormatException.class);
        inspect(NullPointerException.class);
        inspect(OutOfMemoryError.class);
        inspect(AssertionError.class);
    }
}
```

Biên dịch và chạy với Java 17 trở lên:

```bash
javac --release 17 ExceptionHierarchyDemo.java
java ExceptionHierarchyDemo
```

**stdout chính xác:**

```text
IOException | unchecked=false | runtime=false | error=false
Exception | unchecked=false | runtime=false | error=false
NumberFormatException | unchecked=true | runtime=true | error=false
NullPointerException | unchecked=true | runtime=true | error=false
OutOfMemoryError | unchecked=true | runtime=false | error=true
AssertionError | unchecked=true | runtime=false | error=true
```

`Class.isAssignableFrom` trả về `true` khi đối tượng `Class` ở bên trái đại diện cho kiểu cha hoặc chính kiểu của đối tượng `Class` truyền vào. Vì thế `RuntimeException.class.isAssignableFrom(NumberFormatException.class)` là `true`; quan hệ này được xác định từ phân cấp kế thừa, không phụ thuộc tên lớp hoặc nội dung message.

Ví dụ không tạo hay ném `OutOfMemoryError`: nó chỉ **kiểm tra quan hệ kiểu**, an toàn và có thể dự đoán kết quả.

## 4. Những `RuntimeException` thường gặp

`RuntimeException` biểu diễn nhiều tình huống xảy ra trong khi ứng dụng hoặc thư viện vận hành. Java và thư viện chuẩn cung cấp hàng loạt lớp con cụ thể để thông báo bản chất vấn đề:

| Loại | Tình huống điển hình | Hướng xử lý |
| --- | --- | --- |
| `NullPointerException` | Dereference tham chiếu `null`, gọi method/truy cập field không hợp lệ | Sửa nguyên nhân `null`, làm rõ hợp đồng đầu vào và trạng thái |
| `IllegalArgumentException` | Bên gọi truyền đối số vi phạm hợp đồng | Kiểm tra điều kiện và hợp đồng của API |
| `NumberFormatException` | `Integer.parseInt("abc")` không chuyển được sang số nguyên | Xác thực dữ liệu văn bản, báo lỗi nhập phù hợp |
| `IllegalStateException` | Thao tác được yêu cầu khi đối tượng ở trạng thái không cho phép | Xem luồng trạng thái, thời điểm và trình tự lời gọi |
| `IndexOutOfBoundsException` | Truy cập chỉ số ngoài phạm vi của chuỗi/danh sách | Kiểm tra biên và kích thước dữ liệu |
| `ArrayIndexOutOfBoundsException` | Truy cập vị trí không tồn tại trong mảng | Dùng ranh giới `0 <= i < array.length` |
| `ArithmeticException` | Ví dụ `int x = 10 / 0;` trong số học nguyên | Kiểm tra số chia, xử lý điều kiện đầu vào |
| `ClassCastException` | Ép kiểu tham chiếu đến kiểu không tương thích tại runtime | Thiết kế kiểu đúng và kiểm tra kiểu khi cần |
| `UnsupportedOperationException` | Thao tác không được collection hoặc API cụ thể hỗ trợ | Chọn thao tác/triển khai đáp ứng hợp đồng |

Lưu ý quan trọng:

- `NumberFormatException` **kế thừa** `IllegalArgumentException`; `ArrayIndexOutOfBoundsException` **kế thừa** `IndexOutOfBoundsException`. Thứ tự các nhánh `catch` phải phù hợp quan hệ này.
- `ArithmeticException` không có nghĩa mọi phép toán số học Java đều kiểm tra tất cả sai số: phép chia số nguyên cho `0` ném ngoại lệ, nhưng tràn số nguyên thông thường không tự ném ngoại lệ; chia số thực theo IEEE 754 có hành vi riêng (ví dụ `Infinity` hoặc `NaN`).
- `NullPointerException` có thể do JVM tự phát sinh hoặc do mã chủ động ném (ví dụ dùng `Objects.requireNonNull`). Khả năng nhận diện vị trí và thông điệp tùy cách lỗi phát sinh.
- `UnsupportedOperationException` là unchecked; không phải mọi collection đều hỗ trợ mọi phép sửa đổi. Ví dụ `List.of(1, 2).add(3)` sẽ ném `UnsupportedOperationException`.
- Các exception khác trong hệ Java cũng có thể thuộc nhóm unchecked; danh sách ở đây nhằm nhận diện những trường hợp quan trọng nhất.

### Lỗi do người dùng hay lỗi của lập trình viên?

Một ngoại lệ như `NumberFormatException` có thể phát sinh vì người dùng gõ chữ vào ô số. Tầng giao tiếp có thể bắt lỗi và trả thông báo xác thực **bình thường**. Nhưng nếu chính mã nội bộ chuyển đổi sai một cấu hình đã được kiểm tra, nó có thể là bug hoặc lỗi dữ liệu hệ thống.

Phân loại dựa trên **hợp đồng của thao tác** và nguồn dữ liệu, không chỉ tên exception. Không nên đổi mọi trường hợp “người dùng nhập sai” thành một chuỗi unchecked exception truyền qua cả hệ thống nếu ứng dụng vốn có nhánh xác thực rõ ràng.

## 5. Unchecked exception được ném, lan truyền và bắt thế nào?

Cơ chế lan truyền không khác checked exception. Khi `throw` được thực thi, các câu lệnh tiếp theo trên nhánh thực thi đó bị bỏ qua; JVM tìm `catch` có kiểu tương thích trong khối `try` hiện tại rồi các phương thức gọi phía ngoài.

Vì unchecked exception không bắt buộc `throws`, **không có `throws` không chứng minh phương thức sẽ không thất bại**. Ví dụ:

```java
static int parseQuantity(String text) {
    int quantity = Integer.parseInt(text);
    if (quantity <= 0) {
        throw new IllegalArgumentException("Số lượng phải dương");
    }
    return quantity;
}
```

Đây là đoạn phương thức minh họa, không phải một chương trình độc lập. Hàm không khai báo `throws NumberFormatException` hay `throws IllegalArgumentException` mà vẫn biên dịch hợp lệ; bên gọi vẫn có thể nhận ngoại lệ lúc chạy.

Bạn **có thể** khai báo `throws IllegalArgumentException` để tài liệu hóa hợp đồng:

```java
static int parseQuantity(String text) throws IllegalArgumentException {
    return Integer.parseInt(text);
}
```

Đây cũng chỉ là phương thức minh họa. Khai báo đó **không bắt buộc** phía gọi bắt hoặc khai báo lại. Và `throws` không “bật” khả năng ném exception: các exception unchecked đã có thể xảy ra dù chữ ký không liệt kê chúng.

### Ví dụ chạy đầy đủ với nhiều nhánh

Lưu thành `UncheckedFlowDemo.java`:

```java
public class UncheckedFlowDemo {
    static int parsePositive(String text) {
        int value = Integer.parseInt(text);
        if (value <= 0) {
            throw new IllegalArgumentException("Số lượng phải lớn hơn 0");
        }
        return value;
    }

    static int price(int quantity, boolean available) {
        if (!available) {
            throw new IllegalStateException("Bộ tính giá chưa sẵn sàng");
        }
        return quantity * 20;
    }

    public static void main(String[] args) {
        String[] texts = {"2", "abc", "0", "3"};
        boolean[] available = {true, true, true, false};

        for (int i = 0; i < texts.length; i++) {
            String input = texts[i];

            try {
                int quantity = parsePositive(input);
                System.out.println(input + " -> " + price(quantity, available[i]));
            } catch (NumberFormatException e) {
                System.out.println(input + " -> không phải số nguyên");
            } catch (IllegalArgumentException e) {
                System.out.println(input + " -> " + e.getMessage());
            } catch (IllegalStateException e) {
                System.out.println(input + " -> " + e.getMessage());
            }
        }
        System.out.println("Hoàn tất vòng lặp");
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 --release 17 UncheckedFlowDemo.java
java UncheckedFlowDemo
```

**stdout chính xác** với console hiển thị UTF-8:

```text
2 -> 40
abc -> không phải số nguyên
0 -> Số lượng phải lớn hơn 0
3 -> Bộ tính giá chưa sẵn sàng
Hoàn tất vòng lặp
```

Giải thích: đầu vào `"2"` đi theo đường thành công. `"abc"` khiến `parseInt` ném `NumberFormatException`. `"0"` chuyển đổi số thành công rồi điều kiện do ứng dụng đặt ra ném `IllegalArgumentException`. `"3"` qua bước chuyển đổi nhưng `price` ném `IllegalStateException` do trạng thái không sẵn sàng. Cả ba exception được `catch` phù hợp xử lý; vòng lặp có thể tiếp tục.

Hai nhánh `catch` đầu minh họa nguyên tắc **bắt lớp con trước lớp cha**: `NumberFormatException` là lớp con của `IllegalArgumentException`. Đảo thứ tự hai nhánh sẽ dẫn tới lỗi biên dịch vì nhánh bắt `NumberFormatException` phía sau trở nên không thể tới.

Ví dụ dùng `available` như trạng thái kỹ thuật “bộ tính giá đã khởi tạo”. Một tình huống từ chối nghiệp vụ thường gặp, như **hết hàng**, nên có thể được thiết kế thành kết quả có trạng thái thay vì gộp với lỗi `IllegalStateException`.

## 6. `Error` cũng unchecked, nhưng không phải lỗi nghiệp vụ

`Error` là nhánh con trực tiếp của `Throwable`, **khác nhánh `Exception`**. `Error` và các lớp con là unchecked: compiler không bắt buộc caller viết `catch` hoặc `throws`. Tuy nhiên, khác với nhiều `RuntimeException` dùng để báo tham số hoặc trạng thái sai, `Error` thường gắn với vấn đề nghiêm trọng ở môi trường chạy, nạp lớp hoặc bất biến chương trình.

| Họ `Error` | Ví dụ | Cách hiểu và giới hạn xử lý |
| --- | --- | --- |
| `VirtualMachineError` | `OutOfMemoryError`, `StackOverflowError` | Có thể thiếu bộ nhớ/stack hoặc thuật toán đệ quy sai; bắt được không có nghĩa phục hồi an toàn |
| `LinkageError` | `NoClassDefFoundError`, `ExceptionInInitializerError` | Có thể liên quan dependency, classpath hoặc khởi tạo lớp; cần tìm và sửa nguyên nhân |
| `AssertionError` | Assertion bị vi phạm | Có thể do mã hoặc test chủ động tạo; **không** nhất thiết JVM đã hỏng |

Một `OutOfMemoryError` không chỉ có một nguyên nhân `Java heap space`: thông điệp có thể chỉ heap, metaspace hay giới hạn tài nguyên khác tùy JVM. Với `StackOverflowError`, thường phải sửa độ sâu đệ quy/điều kiện dừng; không được xem bắt lỗi là cách chữa thuật toán. Với `NoClassDefFoundError`, không đồng nhất nó với checked `ClassNotFoundException`.

**Cốt lõi của bài unchecked:** không có yêu cầu bắt/khai báo ở *compile time* không có nghĩa lỗi bị bỏ qua ở *runtime*. Những chi tiết vận hành sâu của `VirtualMachineError` không thay đổi quy tắc phân loại này.

## 7. Bắt `Throwable` có thể nuốt cả `Error`

`catch (Exception)` bao phủ checked exception dưới `Exception` **và** mọi `RuntimeException`, nhưng **không** bắt `Error`. `catch (Throwable)` rộng hơn: nó có thể nhận cả `OutOfMemoryError` và `LinkageError`. Vì vậy, mã nghiệp vụ không nên bắt `Throwable` rồi im lặng trả thành công.

```java
// Phản ví dụ: các hàm minh họa nằm trong ngữ cảnh ứng dụng.
try {
    runTask();
} catch (Throwable ignored) {
    // Rất nguy hiểm: cũng nuốt cả Error.
}
```

Bắt rộng chỉ có thể hợp lý ở ranh giới đặc biệt, ví dụ test runner hoặc hạ tầng chẩn đoán có chính sách ghi nhận, cách ly và ném lại rõ ràng. Khi biết cách xử lý cụ thể, nên bắt **loại lỗi cụ thể**. Không dùng `catch (Throwable)` thay cho dọn dẹp: `try-with-resources` hoặc `finally` phù hợp hơn để bảo toàn vòng đời tài nguyên trong luồng thực thi Java thông thường.

## 8. Tự tạo unchecked exception khi hợp đồng thực sự cần

Trong một số API, một điều kiện bất thường có ý nghĩa riêng và bên gọi cần bắt chính xác theo loại. Có thể tạo lớp con của `RuntimeException`:

```java
public class CustomUncheckedDemo {
    static class InvalidTransitionException extends RuntimeException {
        InvalidTransitionException(String message) {
            super(message);
        }
    }

    enum State {
        CREATED, SHIPPED
    }

    static void ship(State state) {
        if (state != State.CREATED) {
            throw new InvalidTransitionException(
                    "Chỉ được gửi đơn ở trạng thái CREATED");
        }
        System.out.println("Đã gửi đơn");
    }

    public static void main(String[] args) {
        ship(State.CREATED);
        try {
            ship(State.SHIPPED);
        } catch (InvalidTransitionException e) {
            System.out.println(e.getMessage());
        }
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 --release 17 CustomUncheckedDemo.java
java CustomUncheckedDemo
```

**stdout chính xác** với UTF-8:

```text
Đã gửi đơn
Chỉ được gửi đơn ở trạng thái CREATED
```

`InvalidTransitionException` kế thừa `RuntimeException` nên cũng unchecked; `ship` không cần khai báo `throws`. Bên gọi vẫn có thể bắt đúng loại để xử lý.

Tuy nhiên, không nhất thiết phải dùng exception cho mọi trạng thái đơn hàng. Nếu “không thể gửi vì đơn đã được gửi” là trường hợp thông thường mà hệ thống cần thông báo cho người dùng, trả về kiểu kết quả như `ALREADY_SHIPPED` có thể rõ ràng hơn. Custom exception thích hợp hơn khi thao tác bị gọi trái tiền điều kiện/bất biến của API và cần dừng luồng hiện tại.

Không tự tạo lớp con của `Error` để biểu diễn lỗi validation, hết hàng hay giao dịch bị từ chối. Điều đó khiến người đọc hiểu sai mức độ nghiêm trọng và bỏ qua các ranh giới xử lý `Exception` bình thường.

Khi cần tạo exception từ nguyên nhân khác, nên có constructor cho message và cause:

```java
// Minh họa định nghĩa kiểu, không chứa main.
class ConfigurationRuntimeException extends RuntimeException {
    ConfigurationRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

Giữ `cause` giúp chẩn đoán lỗi gốc. Chỉ chuyển checked thành unchecked khi hợp đồng của lớp hiện tại cho phép và tầng gọi biết cách ứng phó; tránh bọc lại chỉ để lách quy tắc checked exception.

## 9. Checked và unchecked khác nhau ở đâu trong mã nguồn?

Xét hai phương thức minh họa:

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class FileAndInput {
    static String load(Path path) throws IOException {
        return Files.readString(path);
    }

    static int parse(String text) {
        return Integer.parseInt(text);
    }
}
```

Mã này là lớp Java hợp lệ, nhưng không có phương thức `main`. `load` có thể ném `IOException` nên khai báo `throws IOException`; bên gọi không ở trong ngữ cảnh `catch` phù hợp hoặc không `throws` tiếp sẽ gặp lỗi biên dịch khi gọi. `parse` có thể ném `NumberFormatException` nhưng không bắt buộc khai báo và bên gọi không bị buộc phải bắt.

Hai điều thường bị hiểu sai:

1. Nếu `parse` tự ý thêm `throws NumberFormatException` vào chữ ký, **kiểu vẫn unchecked**; nghĩa vụ compile time ở caller không đổi.
2. Nếu một phương thức khai báo `throws Exception`, bên gọi phải xét nghĩa vụ bắt/khai báo **kiểu checked `Exception`** được công bố trong chữ ký, dù bên trong có lúc chỉ ném một unchecked exception. Hợp đồng khai báo có ý nghĩa riêng.

### Ghi đè phương thức và `throws`

Quy tắc ghi đè liên quan exception checked: phương thức override thường không được thêm một checked exception rộng hơn những gì phương thức cha cho phép. Nhưng unchecked exception không bị giới hạn bởi quy tắc này.

Ví dụ mã Java hợp lệ:

```java
class ParentWorker {
    void execute() {
        // Hợp đồng không có checked exception.
    }
}

class ChildWorker extends ParentWorker {
    @Override
    void execute() throws IllegalStateException {
        throw new IllegalStateException("Chưa khởi tạo");
    }
}
```

Đây là lớp minh họa không có `main`. `IllegalStateException` là unchecked nên chữ ký override hợp lệ. Nếu thay bằng `throws IOException`, mã này sẽ không biên dịch vì khai báo checked exception mới mà phương thức cha không cho phép.

## 10. Thiết kế xử lý `RuntimeException` đúng tầng

Tầng phát hiện và tầng quyết định phản hồi có thể khác nhau:

1. **Lớp thư viện/API:** kiểm tra đối số và bất biến, ném `IllegalArgumentException` hoặc `IllegalStateException` khi hợp đồng bị vi phạm; chọn loại cụ thể và thông điệp hữu ích.
2. **Tầng nhận yêu cầu:** xác thực dữ liệu người dùng, chuyển lỗi parse thành thông điệp có cấu trúc khi đó là trường hợp dự kiến. Không hiển thị stack trace cho người dùng.
3. **Tầng nghiệp vụ:** trả trạng thái domain cho nhánh từ chối hợp lệ; giữ lỗi kỹ thuật/bất biến riêng.
4. **Ranh giới ứng dụng:** ghi log nguyên nhân, phân loại phản hồi, dừng yêu cầu hoặc đánh dấu job thất bại đúng chính sách.

Với HTTP API, việc trả `400` cho dữ liệu nhập không hợp lệ, `409` cho xung đột nghiệp vụ hoặc `500` cho lỗi nội bộ là **lựa chọn hợp đồng API**, không phải quy tắc tự động của Java. Cùng `IllegalArgumentException` có thể xuất phát từ nhập liệu hoặc bug nội bộ; chỉ dựa vào tên lớp để trả `400` có thể che khuất lỗi lập trình.

**Không dùng unchecked exception làm đường tắt cho mọi vấn đề**. Nếu một thư viện ném `IOException`, thay nó bằng `RuntimeException` không tự tạo chiến lược phục hồi. Chỉ chuyển loại khi cần ranh giới trừu tượng phù hợp và bảo toàn nguyên nhân qua `cause`.

**Không nên log cùng một exception đầy đủ ở mọi tầng**, vì tạo nhiều bản ghi trùng nhau; chọn nơi có đủ ngữ cảnh, giữ stack trace/cause cần thiết, không lộ mật khẩu, token hoặc dữ liệu nhạy cảm.

## 11. Sai lầm thường gặp

| Nhận định sai | Cách hiểu đúng |
| --- | --- |
| “Unchecked tức là JVM không phát hiện.” | JVM vẫn ném exception; chỉ không có nghĩa vụ `catch-or-declare` ở compiler. |
| “Mọi lớp con của `Exception` là checked.” | `RuntimeException` và các lớp con là unchecked. |
| “`RuntimeException` và `Error` nên bắt như nhau.” | Chúng cùng unchecked nhưng `Error` thường cho thấy vấn đề rất nghiêm trọng, khó tiếp tục an toàn. |
| “Không có `throws` nghĩa là phương thức không ném exception.” | Unchecked exception có thể xuất hiện không cần khai báo. |
| “Thêm `throws RuntimeException` buộc caller bắt.” | Không; unchecked vẫn không bị quy tắc bắt/khai báo bắt buộc. |
| “`catch (Exception e)` bắt được `OutOfMemoryError`.” | Không, vì `Error` nằm ngoài nhánh `Exception`. |
| “Bắt `Throwable` để ứng dụng không thể sập.” | Có thể nuốt lỗi nghiêm trọng, cố tiếp tục khi trạng thái JVM không an toàn. |
| “`NoClassDefFoundError` và `ClassNotFoundException` là cùng loại.” | Một bên là unchecked `Error`, bên kia là checked `Exception`. |
| “`assert` thay thế mọi validation.” | Assertions có thể bị tắt; không dùng để kiểm tra dữ liệu không tin cậy. |
| “Mỗi lỗi người dùng đều là bug.” | Có thể chuyển exception parse sang kết quả xác thực dự kiến ở ranh giới phù hợp. |
| “`Error` có nghĩa compile error.” | `Error` là lớp Java, còn compile error là chẩn đoán của trình biên dịch. |
| “Bắt exception rồi bỏ qua nghĩa là đã xử lý.” | Có thể làm mất nguyên nhân, trả kết quả giả và khiến trạng thái tiếp tục sai. |

## 12. Câu hỏi và tình huống luyện tập

**1. `NumberFormatException` có checked không?**  
Không. Nó là lớp con của `IllegalArgumentException`, tiếp tục kế thừa `RuntimeException`.

**2. `NullPointerException` có cần khai báo trong `throws` của mọi phương thức có thể ném nó không?**  
Không. Có thể tài liệu hóa hợp đồng bằng chú thích hoặc thiết kế API rõ ràng, nhưng trình biên dịch không đòi hỏi khai báo unchecked exception.

**3. `IOException` có unchecked vì đôi khi xảy ra lúc runtime không?**  
Không. “Checked/unchecked” được xác định từ phân cấp `Throwable`, không phải thời điểm thực thi: `IOException` là checked.

**4. Đặt `catch (IllegalArgumentException e)` trước `catch (NumberFormatException e)` có hợp lệ không?**  
Không nếu hai nhánh kế tiếp cùng thuộc một `try` theo thứ tự đó. Nhánh cho lớp con không thể tới vì lớp cha đã bắt nó.

**5. Tại sao không nên dùng `catch (Error e)` cho mọi thao tác?**  
Vì nhiều `Error` báo sự cố nền tảng, như JVM hết bộ nhớ hoặc lỗi liên kết lớp. Bắt chúng không khắc phục nguyên nhân và có thể tiếp tục trong trạng thái hỏng.

**6. `assert condition` luôn kiểm tra `condition` không?**  
Không. Việc thực thi assertion phụ thuộc cấu hình bật/tắt (`-ea` và các tùy chọn tương ứng). Do đó không thay kiểm tra đầu vào hoặc điều kiện bảo mật bằng assertion.

**7. Lỗi không tìm được lớp khi `Class.forName` được gọi có luôn là `NoClassDefFoundError` không?**  
Không. `Class.forName` có thể ném checked `ClassNotFoundException`; `NoClassDefFoundError` là lỗi khác trong nhánh `LinkageError`.

**8. Khi trạng thái đơn hàng đã SHIPPED, có bắt buộc ném custom unchecked exception không?**  
Không. Có thể dùng kết quả domain thông thường hoặc exception tùy hợp đồng và ý nghĩa của lời gọi sai trạng thái.

**9. Chỉ cần xóa `throws IOException` khỏi hàm đọc tệp để chuyển nó thành unchecked không?**  
Không. Nếu bên trong vẫn gọi API có checked `IOException` mà không xử lý, phương thức sẽ không biên dịch. Muốn đổi hợp đồng cần bắt và xử lý/chuyển đổi một cách có chủ ý.

**10. Một unchecked exception bị bỏ qua sẽ làm toàn bộ JVM kết thúc trong mọi trường hợp không?**  
Không. Nó lan tới `catch` hoặc ranh giới xử lý; nếu thoát khỏi một thread, thread đó có thể kết thúc, trong khi các thread khác vẫn tồn tại. Ứng dụng console đơn giản có thể kết thúc khi exception thoát khỏi `main`.

## 13. Tự kiểm tra trước khi chọn xử lý

Khi thấy một lỗi, xác định ba vấn đề theo thứ tự:

- **Nó thuộc nhánh nào?** `RuntimeException`, `Error`, hay checked exception khác? Phân cấp xác định nghĩa vụ của compiler.
- **Nguyên nhân và phạm vi ảnh hưởng là gì?** Đầu vào sai, hợp đồng bị vi phạm, trạng thái sai, thư viện thiếu, JVM cạn nguồn lực hay một bất biến bị phá vỡ?
- **Tầng hiện tại có khả năng xử lý an toàn không?** Nếu có, bắt đúng loại và đưa ra phản hồi/khôi phục có ý nghĩa. Nếu không, cho phép lỗi lan tới ranh giới có chính sách phù hợp; đối với nhiều `Error`, phải sửa nguyên nhân và đánh giá lại khả năng tiếp tục hoạt động.

**Ghi nhớ:** `RuntimeException` và `Error` cùng là **unchecked**, vì Java không bắt buộc caller bắt hoặc khai báo chúng. Sự khác nhau về ý nghĩa và khả năng phục hồi mới là cơ sở chọn cách xử lý. Đọc đúng hệ phân cấp giúp tránh đánh đồng dữ liệu đầu vào sai với lỗi JVM, tránh bắt quá rộng, và thiết kế API có hợp đồng rõ ràng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-hierarchy-006">Phân biệt quy tắc checked với khả năng phục hồi thực tế</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt quy tắc checked với khả năng phục hồi thực tế trong Java

Trong Java, *checked exception* và *unchecked exception* mô tả cách **trình biên dịch kiểm soát việc khai báo hoặc xử lý ngoại lệ**. Trong khi đó, **khả năng phục hồi** (recoverability) là câu hỏi ở thời điểm chạy: chương trình có thể thực hiện hành động cụ thể nào để tiếp tục đúng đắn sau thất bại?

Hai khái niệm có liên quan khi thiết kế API, nhưng **không đồng nhất**:

- Ngoại lệ *checked* vẫn có thể là lỗi mà phương thức hiện tại hoặc cả ứng dụng không thể khắc phục.
- Ngoại lệ *unchecked* vẫn có thể biểu diễn một tình huống dự kiến và có phương án xử lý an toàn, chẳng hạn dữ liệu người dùng không thể chuyển thành số.
- `catch` làm chương trình **chặn và xử lý luồng điều khiển** tại một điểm; riêng việc bắt được đối tượng ngoại lệ **không chứng minh đã khôi phục thành công**.

Mục tiêu bài học là phân biệt nghĩa vụ của ngôn ngữ với quyết định xử lý lỗi dựa trên nghiệp vụ, trạng thái và ranh giới trách nhiệm.

## 1. Phân cấp ngoại lệ và ý nghĩa của checked/unchecked

~~~text
java.lang.Throwable
├── java.lang.Error                           [unchecked]
│   └── java.lang.OutOfMemoryError            [unchecked]
└── java.lang.Exception                       [checked]
    ├── java.io.IOException                   [checked]
    ├── java.text.ParseException              [checked]
    └── java.lang.RuntimeException            [unchecked]
        ├── java.lang.IllegalArgumentException[unchecked]
        │   └── java.lang.NumberFormatException [unchecked]
        └── java.lang.IllegalStateException   [unchecked]
~~~

Đây là một phần của cây, không phải toàn bộ phân cấp. Đặc biệt, `NumberFormatException` là **lớp con của `IllegalArgumentException`**, không phải một nhánh ngang hàng.

Theo quy tắc Java, các lớp kế thừa `Throwable` **trừ** `RuntimeException` và `Error` cùng mọi lớp con của hai nhánh đó là *checked*. Vì vậy:

- `IOException` và `ParseException` là checked.
- `RuntimeException`, `NumberFormatException`, `IllegalStateException` và `Error` là unchecked.
- `RuntimeException` **vẫn kế thừa `Exception`**, nhưng được miễn quy tắc checked.
- Phân loại này không phải mức độ nghiêm trọng và không đo khả năng khôi phục.

### Quy tắc của trình biên dịch

Khi một thao tác **có thể ném checked exception** theo thông tin kiểu mà trình biên dịch thấy, mã bao quanh phải xử lý hoặc khai báo ngoại lệ theo luật Java. Hai cách thông dụng:

1. Bắt ngoại lệ bằng `try/catch` ở tầng đủ khả năng quyết định.
2. Cho phép lan truyền bằng cách khai báo `throws` ở chữ ký phương thức.

Ví dụ:

~~~java
static String readConfig(java.nio.file.Path path) throws java.io.IOException {
    return java.nio.file.Files.readString(path);
}
~~~

Đây là **đoạn phương thức minh họa**, chưa phải chương trình chạy độc lập. `Files.readString` có thể ném `IOException`; ví dụ cho phép chuyển tiếp qua `throws IOException`. Nếu nơi gọi không bắt hoặc khai báo checked exception tương ứng, trình biên dịch có thể báo `unreported exception ...; must be caught or declared to be thrown`.

Ngược lại:

~~~java
static int parsePort(String text) {
    return Integer.parseInt(text);
}
~~~

`Integer.parseInt` có thể ném `NumberFormatException`, nhưng không phải thêm `throws NumberFormatException`. Ngoại lệ vẫn xảy ra và lan truyền **khi chạy** nếu đầu vào không hợp lệ.

**`throws` là hợp đồng tĩnh**, không phải lệnh thực hiện khôi phục. **`throw` là lệnh phát sinh ngoại lệ**. **`catch` là nơi có thể xử lý ngoại lệ**, nhưng hành động trong khối `catch` mới quyết định hậu quả.

## 2. Hai trục độc lập: phân loại và khả năng phục hồi

Có thể hình dung mỗi ngoại lệ theo hai câu hỏi riêng:

1. **Compiler:** Kiểu ngoại lệ này thuộc checked hay unchecked?
2. **Runtime và nghiệp vụ:** Ở vị trí đang xét, có cách an toàn nào để đạt mục tiêu, đưa trạng thái về hợp lệ hoặc thông báo thất bại đúng cách không?

| | Có phương án phục hồi phù hợp | Không có phương án phục hồi tại đây |
| --- | --- | --- |
| **Checked** | `IOException` khi có bản cấu hình dự phòng hợp lệ; đọc lại tài nguyên khi chính sách cho phép | `IOException` do mất tệp bắt buộc mà không có nguồn thay thế; `SQLException` khi giao dịch không còn hợp lệ |
| **Unchecked** | `NumberFormatException` khi giao diện yêu cầu người dùng nhập lại số hợp lệ | `IllegalStateException` khi thao tác vi phạm trạng thái nội bộ; `NullPointerException` do lỗi logic chưa được sửa |

**Các ô trong bảng là ví dụ phụ thuộc ngữ cảnh, không phải tính chất cố định của lớp ngoại lệ.** Cùng một `IOException` có thể phục hồi được ở một màn hình có chức năng chọn lại tệp, nhưng không thể phục hồi trong một bước khởi tạo bắt buộc. Cùng một `NumberFormatException` có thể là lỗi dữ liệu dự kiến ở UI, nhưng là lỗi lập trình nếu chuỗi là hằng số do chính chương trình định nghĩa và đáng lẽ luôn hợp lệ.

Cần phân biệt thêm ba kết quả:

- **Phục hồi để hoàn thành mục tiêu:** Dùng nguồn dự phòng đã kiểm chứng; yêu cầu nhập lại và nhận giá trị đúng; thử lại theo chính sách an toàn.
- **Xử lý thất bại có kiểm soát:** Dừng thao tác, trả lỗi có nghĩa, giữ giao dịch nhất quán, giải phóng tài nguyên. Đây là *error handling* đúng đắn, **chưa chắc là recovery**.
- **Che giấu thất bại:** Bắt ngoại lệ rồi bỏ qua, trả giá trị giả không có hợp đồng, hoặc tiếp tục khi trạng thái đã hỏng. Cách này có thể làm hậu quả khó phát hiện hơn.

Khả năng phục hồi thuộc về **ngữ cảnh tầng gọi và chính sách hệ thống**. Không nên gán một thuộc tính `recoverable=true/false` cố định cho toàn bộ lớp ngoại lệ.

## 3. Chương trình Java 17+ thể hiện đủ bốn trường hợp

Ví dụ giả lập nguồn cấu hình chính bị lỗi theo cách có thể dự đoán. Một quy trình có bản dự phòng nên khôi phục được; quy trình khác phải dừng bước khởi tạo. Sau đó chương trình xử lý giá trị tuổi người dùng nhập sai (unchecked nhưng có hướng khắc phục) và một lỗi trạng thái đối tượng (unchecked, không thể tiếp tục thao tác đó).

Tạo tệp **`RecoveryDemo.java`**:

~~~java
import java.io.IOException;

public class RecoveryDemo {
    public static void main(String[] args) {
        System.out.println("1. Checked - có bản dự phòng");
        loadWithFallback();

        System.out.println("2. Checked - không có bản dự phòng");
        loadWithoutFallback();

        System.out.println("3. Unchecked - có giá trị nhập lại");
        int age = parseAgeWithCorrection("hai mươi", "20");
        System.out.println("Tuổi hợp lệ: " + age);

        System.out.println("4. Unchecked - trạng thái không hợp lệ");
        try {
            requireReady(false);
        } catch (IllegalStateException e) {
            System.out.println("Hủy thao tác: " + e.getMessage());
        }
    }

    // IOException vẫn là checked dù dữ liệu được giả lập cho ví dụ.
    static String readConfiguration(String source) throws IOException {
        if ("primary".equals(source)) {
            throw new IOException("nguồn chính không phản hồi");
        }
        if ("backup".equals(source)) {
            return "SAFE";
        }
        throw new IOException("nguồn cấu hình không tồn tại");
    }

    static void loadWithFallback() {
        try {
            String mode = readConfiguration("primary");
            System.out.println("Chế độ: " + mode);
        } catch (IOException primaryFailure) {
            try {
                String mode = readConfiguration("backup");
                System.out.println("Đã chuyển sang bản dự phòng: " + mode);
            } catch (IOException backupFailure) {
                backupFailure.addSuppressed(primaryFailure);
                System.out.println(
                        "Không tải được cấu hình: " + backupFailure.getMessage());
            }
        }
    }

    static void loadWithoutFallback() {
        try {
            readConfiguration("primary");
            System.out.println("Khởi tạo thành công");
        } catch (IOException e) {
            System.out.println("Dừng bước khởi tạo: " + e.getMessage());
        }
    }

    static int parseAgeWithCorrection(String first, String corrected) {
        try {
            return Integer.parseInt(first);
        } catch (NumberFormatException e) {
            System.out.println("Giá trị nhập sai: " + first);
            return Integer.parseInt(corrected);
        }
    }

    static void requireReady(boolean ready) {
        if (!ready) {
            throw new IllegalStateException("đối tượng chưa sẵn sàng");
        }
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~bash
javac RecoveryDemo.java
java RecoveryDemo
~~~

Kết quả chuẩn:

~~~text
1. Checked - có bản dự phòng
Đã chuyển sang bản dự phòng: SAFE
2. Checked - không có bản dự phòng
Dừng bước khởi tạo: nguồn chính không phản hồi
3. Unchecked - có giá trị nhập lại
Giá trị nhập sai: hai mươi
Tuổi hợp lệ: 20
4. Unchecked - trạng thái không hợp lệ
Hủy thao tác: đối tượng chưa sẵn sàng
~~~

### Phân tích từng nhánh

**Trường hợp 1 — checked và phục hồi được:** `readConfiguration("primary")` ném `IOException`. `loadWithFallback` đã có nguồn `"backup"` được chấp nhận nên đọc thành công và in `SAFE`. Điều làm phục hồi được là **nguồn thay thế cùng chính sách dùng nguồn thay thế**, không phải từ khóa `checked`.

**Trường hợp 2 — checked và không phục hồi ở bước hiện tại:** Cùng kiểu `IOException`, cùng nguồn lỗi. `loadWithoutFallback` không có giải pháp thay thế nên báo hỏng và dừng bước khởi tạo. Phương thức có `catch` nhưng **không hoàn tất mục tiêu tải cấu hình**. Trong ứng dụng thực, bên gọi phải hiểu kết quả thất bại; có thể chuyển tiếp ngoại lệ hoặc trả kết quả thể hiện rõ trạng thái thay vì in rồi tiếp tục như ví dụ trình diễn.

**Trường hợp 3 — unchecked và phục hồi được:** `Integer.parseInt("hai mươi")` ném `NumberFormatException`. Phần xử lý cung cấp giá trị mới `"20"` và hoàn thành mục tiêu. Chương trình không cần khai báo checked exception để thực hiện nhánh phục hồi. Ví dụ giả lập người dùng nhập lại; ở ứng dụng thực, cần xác thực giá trị mới và cho phép nhập lại an toàn, tránh giả định mọi lần nhập lại sẽ đúng.

**Trường hợp 4 — unchecked và không phục hồi:** `requireReady(false)` phát hiện tiền điều kiện về trạng thái không được đáp ứng. Khối `catch` hủy thao tác đó và hiển thị lý do. Nó không biến trạng thái đối tượng thành hợp lệ; muốn thực hiện lại phải sửa quy trình khởi tạo ở nơi có thẩm quyền.

Ở nhánh nguồn dự phòng lỗi tiếp, lệnh `backupFailure.addSuppressed(primaryFailure)` giữ thất bại ban đầu để phục vụ chẩn đoán. Nhánh này **không xảy ra trong lần chạy minh họa** vì nguồn `backup` hoạt động. Cơ chế suppressed exceptions hữu ích khi cần giữ nhiều lỗi có liên quan; trong hệ thống thực thường cần chuyển tiếp hoặc ghi nhận lỗi tổng hợp theo hợp đồng thay vì chỉ in thông báo.

## 4. Nghĩa vụ của `catch/throws` không tương đương chính sách lỗi

Xem hai cách dùng với checked exception:

~~~java
static String load(java.nio.file.Path path) throws java.io.IOException {
    return java.nio.file.Files.readString(path);
}
~~~

Phương thức `load` chọn **chuyển trách nhiệm ra ngoài**. Đây có thể là lựa chọn tốt vì tầng gọi biết có tệp dự phòng hay có nên hiện thông báo chọn tệp khác. Khai báo `throws IOException` thể hiện loại thất bại mà người gọi phải cân nhắc, chứ không hứa rằng có thể khôi phục.

~~~java
static String loadOrDefault(java.nio.file.Path path)
        throws java.io.IOException {
    try {
        return java.nio.file.Files.readString(path);
    } catch (java.nio.file.NoSuchFileException e) {
        return "default";
    }
}
~~~

Đây là hai **đoạn phương thức độc lập**, chưa có `main`. Đoạn thứ hai chỉ đúng về nghiệp vụ nếu **hợp đồng cho phép dùng giá trị mặc định khi tệp không tồn tại**. Các lỗi I/O khác vẫn có thể lan qua `throws IOException`. Nếu tệp là bí mật cấu hình bắt buộc, việc dùng `"default"` im lặng có thể nguy hiểm hoặc tạo cấu hình sai.

Khi checked exception lan qua nhiều lớp, có thể chọn khai báo kiểu cụ thể; không nên mặc định dùng `throws Exception` cho mọi API. Một số framework hoặc ranh giới kiến trúc quyết định chuyển ngoại lệ thấp tầng thành loại lỗi miền (*domain exception*) để diễn đạt hợp đồng ở tầng cao hơn. Khi chuyển, nên giữ nguyên nhân:

~~~java
static String loadRequired(java.nio.file.Path path) {
    try {
        return java.nio.file.Files.readString(path);
    } catch (java.io.IOException e) {
        throw new IllegalStateException(
                "Không thể nạp cấu hình bắt buộc", e);
    }
}
~~~

Đây là đoạn phương thức minh họa. Việc chuyển `IOException` sang `IllegalStateException` làm **thay đổi yêu cầu kiểm tra tại thời điểm biên dịch của người gọi**, không làm lỗi biến mất và không khiến nó tự phục hồi. `getCause()` của ngoại lệ mới trả về `IOException` gốc.

## 5. Tiêu chí thực tế để quyết định có phục hồi hay không

Trước khi bắt một ngoại lệ nhằm tiếp tục hoạt động, hãy kiểm tra:

1. **Mục tiêu có còn đạt được không?** Có tệp khác, nguồn dự phòng, cách xác thực lại dữ liệu hoặc cách chạy lại thao tác hay không?
2. **Dữ liệu và trạng thái có còn nhất quán không?** Nếu giao dịch đã có thay đổi một phần, cần rollback hoặc cơ chế bù trừ trước khi thử lại.
3. **Thao tác có an toàn khi lặp lại không?** Đọc tài nguyên có thể thử lại; ghi tiền, gửi yêu cầu hoặc tạo bản ghi có thể gây tác dụng phụ lặp, cần cơ chế idempotency hoặc kiểm soát giao dịch.
4. **Lỗi có tạm thời không?** Timeout mạng có thể là tạm thời; tệp cấu hình sai định dạng thường không tự đúng sau một lần retry. Phải giới hạn số lần thử, thời gian chờ và ngân sách tài nguyên.
5. **Tầng này có đủ thẩm quyền không?** Hàm đọc tệp có thể không biết dùng bản mặc định hay buộc khởi động thất bại. Chuyển lỗi lên tầng điều phối thường chính xác hơn.
6. **Có giữ được nguyên nhân và ngữ cảnh không?** Thông báo, cause, suppressed exception, mã lỗi hoặc log có cấu trúc giúp phân tích khi không thể khôi phục.

Các câu hỏi này áp dụng cho **cả checked lẫn unchecked**.

### Ví dụ theo ranh giới ứng dụng

| Ranh giới | Ngoại lệ có thể gặp | Cách ứng xử phù hợp |
| --- | --- | --- |
| Màn hình nhập biểu mẫu | `NumberFormatException` (unchecked) | Hiển thị trường nhập sai, giữ dữ liệu khác, yêu cầu sửa |
| Bộ nạp cấu hình | `IOException` (checked) | Dùng nguồn dự phòng **nếu hợp đồng cho phép**, hoặc báo không thể khởi tạo |
| Dịch vụ nghiệp vụ | `IllegalArgumentException` (unchecked) | Trả thông tin đầu vào không hợp lệ; không thử lại cùng dữ liệu vô hạn |
| Tầng truy cập tài nguyên | `IOException` (checked) | Cung cấp lỗi có ngữ cảnh, đóng tài nguyên, để tầng trên quyết định |
| Bộ điều phối tác vụ | Ngoại lệ bất ngờ chưa xử lý | Ghi nhận, hủy hoặc đánh dấu tác vụ thất bại; không âm thầm coi tác vụ thành công |

“Xử lý phù hợp” không nhất thiết mang nghĩa “chạy tiếp bình thường”. Một kết quả **thất bại minh bạch, trạng thái an toàn** thường tốt hơn việc che giấu lỗi để trả về thành công giả.

## 6. Các bẫy thường gặp

### Bẫy 1: “Checked nghĩa là chắc chắn phục hồi được”

Không. `IOException` là checked dù mất tệp bắt buộc không có dự phòng. `catch` có thể chỉ thêm ngữ cảnh và chuyển tiếp; yêu cầu biên dịch không cung cấp giải pháp nghiệp vụ.

### Bẫy 2: “Unchecked luôn là bug, đừng bao giờ bắt”

Không. `NumberFormatException` từ dữ liệu do người dùng nhập là tình huống hợp lệ để xử lý ở lớp UI. `IllegalArgumentException` từ yêu cầu ngoài hệ thống cũng có thể được ánh xạ thành lỗi xác thực. Tuy nhiên, nếu nguyên nhân là sai invariant nội bộ, nên sửa mã hoặc quy trình thay vì coi mọi lỗi là dữ liệu không hợp lệ.

### Bẫy 3: “Đã `catch` thì hệ thống đã phục hồi”

Ví dụ sai:

~~~java
try {
    saveImportantData();
} catch (Exception ignored) {
    // Tiếp tục và coi như lưu thành công.
}
~~~

Đây là đoạn **phản ví dụ về chính sách lỗi**, không phải chương trình chạy độc lập; `saveImportantData()` đại diện cho thao tác của ứng dụng. Bắt rồi bỏ qua có thể khiến bên gọi hiểu nhầm dữ liệu đã lưu. Hãy xử lý kết quả thất bại đúng hợp đồng: retry có kiểm soát, rollback, trả trạng thái lỗi hoặc chuyển tiếp ngoại lệ.

### Bẫy 4: “Đổi checked sang runtime là giải quyết vấn đề”

Bọc `IOException` trong `RuntimeException` loại bỏ nghĩa vụ `catch/throws` của người gọi nhưng **không khôi phục tài nguyên**. Nếu có lý do thiết kế để chuyển đổi, cần dùng loại lỗi có ý nghĩa và giữ `cause`.

### Bẫy 5: “Retry mọi ngoại lệ một vài lần là ổn”

Retry có ích khi lỗi có khả năng tạm thời, thao tác được phép lặp và vẫn còn ngân sách thời gian. Retry `NumberFormatException` trên cùng chuỗi `"abc"` không khiến chuỗi trở thành số. Retry `IllegalStateException` khi chưa sửa trạng thái có thể chỉ tạo vòng lặp vô ích. Retry lệnh ghi có tác dụng phụ không có bảo vệ idempotency có thể nhân đôi kết quả.

### Bẫy 6: “`catch (Exception e)` là ranh giới khôi phục an toàn”

`catch (Exception e)` bắt cả phần lớn checked exception **và** `RuntimeException` vì quan hệ kế thừa. Khi dùng ở ranh giới hệ thống, phải tránh gộp lỗi lập trình với lỗi có thể dự kiến rồi áp chung cách tiếp tục. Nó cũng không bắt `Error`. Nên bắt loại cụ thể nơi có cách xử lý rõ ràng, và chỉ có xử lý tổng quát ở ranh giới đã định nghĩa chính sách lỗi.

### Bẫy 7: “InterruptedException là tín hiệu cứ retry tiếp”

`InterruptedException` là checked, nhưng thường biểu thị yêu cầu gián đoạn hoặc hủy công việc. Không nên vô điều kiện nuốt ngoại lệ rồi retry. Khi không thể chuyển tiếp trong hợp đồng hiện tại, một cách thường dùng là khôi phục cờ interrupt bằng `Thread.currentThread().interrupt()` và chấm dứt bước đang thực hiện theo chính sách của ứng dụng. Ngữ cảnh quản lý tác vụ quyết định hành động đúng.

## 7. Một quy trình quyết định ngắn khi thiết kế API

Có thể dùng trình tự sau khi bắt gặp thao tác có khả năng thất bại:

~~~text
Thao tác thất bại
       │
       ▼
Có cách đáp ứng mục tiêu an toàn, hợp đồng cho phép?
       ├── Có → Thực hiện fallback / nhập lại / retry có giới hạn
       │         └── Kiểm tra kết quả và trạng thái sau xử lý
       │
       └── Không
             ▼
        Tầng hiện tại có nghĩa vụ thông báo hay chuyển tiếp?
             ├── Có → Gắn ngữ cảnh, giữ cause, trả lỗi hoặc ném tiếp
             └── Không → Đưa lỗi đến ranh giới chịu trách nhiệm
~~~

Sơ đồ nói về **chính sách chạy thật**, độc lập với checked/unchecked. Sau khi chọn cách ứng xử, mới thực hiện cú pháp Java cần thiết: `try/catch`, `throws`, hoặc `throw` theo kiểu ngoại lệ và hợp đồng phương thức.

Nếu thiết kế **loại ngoại lệ riêng**:

- Chọn checked exception khi muốn các bên gọi Java bắt buộc *xét đến* tình huống thất bại trong hợp đồng API. Điều này không bảo đảm họ sẽ có cách phục hồi.
- Chọn unchecked exception khi hợp đồng không yêu cầu cơ chế xử lý bắt buộc ở mọi điểm gọi, ví dụ vi phạm tiền điều kiện, trạng thái, hoặc một loại lỗi miền theo quy ước của hệ thống.
- Ưu tiên loại cụ thể và thông điệp rõ ràng; mô tả trong JavaDoc khi API có tác động quan trọng. Không quyết định checked/unchecked chỉ dựa vào nhãn “lỗi bên ngoài” hay “lỗi lập trình”.

## 8. Tự kiểm tra kiến thức

1. `IOException` xảy ra khi đọc tệp. Vì sao cùng một kiểu ngoại lệ có thể phục hồi được ở màn hình chọn tệp nhưng không thể phục hồi ở giai đoạn khởi tạo bắt buộc?
2. `NumberFormatException` là unchecked. Người dùng nhập `"xyz"` vào trường tuổi. Bạn có thể bắt và yêu cầu nhập lại không? Trình biên dịch có buộc bạn bắt không?
3. Một phương thức bắt `IOException`, ghi log rồi trả `"OK"` dù chưa hoàn thành việc ghi dữ liệu. Đây có phải phục hồi hay không? Rủi ro gì?
4. Khi chuyển `IOException` thành `IllegalStateException`, ngoại lệ gốc nên được lưu ở đâu? Việc chuyển có giúp hoạt động thành công không?
5. Tại sao không nên retry một yêu cầu thanh toán chưa có cơ chế chống xử lý trùng, ngay cả khi lỗi mạng có vẻ tạm thời?
6. Với `InterruptedException`, tại sao “bắt rồi tiếp tục vô hạn” thường là một quyết định sai?
7. Phân biệt: “trình biên dịch bắt buộc xử lý” và “chương trình có thể khôi phục” trả lời hai câu hỏi nào?

**Gợi ý đáp án:** (1) Vì có hay không có phương án dự phòng và chính sách cho phép; (2) có thể bắt để nhập lại, compiler không bắt buộc; (3) không, đây là che giấu thất bại; (4) lưu trong `cause`, không tự sửa lỗi; (5) có thể tạo tác dụng phụ trùng; (6) bỏ qua ý nghĩa hủy/gián đoạn; (7) nghĩa vụ kiểu tại biên dịch so với khả năng tiếp tục đúng đắn khi chạy.

**Ghi nhớ:** *Checked/unchecked là đặc tính của kiểu ngoại lệ trong hệ thống kiểm tra biên dịch Java. Recoverability là đặc tính của tình huống, mục tiêu và chính sách xử lý tại một tầng cụ thể.* Đánh giá hai trục riêng rẽ giúp viết API rõ hợp đồng và xử lý lỗi không làm sai trạng thái ứng dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-hierarchy-007">Quan hệ kiểu cha, kiểu con và lựa chọn catch</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ kiểu cha, kiểu con và lựa chọn `catch` trong Java

Khi một ngoại lệ được ném, Java không tìm `catch` bằng cách so sánh **tên ngoại lệ trong thông điệp**. JVM xét **kiểu thực tế của đối tượng ngoại lệ** và quan hệ kế thừa với kiểu khai báo trong từng nhánh `catch`. Một nhánh bắt kiểu cha có thể bắt cả ngoại lệ thuộc kiểu con. Vì vậy, thứ tự `catch`, phạm vi `try` và tầng xử lý quyết định một lỗi sẽ được xử lý ra sao.

Bài này tập trung vào ba câu hỏi:

1. Ngoại lệ nào là **cha/con** trong hệ thống kiểu của Java?
2. Nếu cùng một lỗi phù hợp với nhiều kiểu `catch`, Java **chọn nhánh nào**?
3. Khi viết API và tổ chức tầng xử lý, nên bắt **kiểu cụ thể** hay **kiểu tổng quát**?

## 1. Cây phân cấp ngoại lệ cần nắm

Các ngoại lệ và lỗi nghiêm trọng trong Java đều thuộc phân cấp `java.lang.Throwable`. Một phần cây quan trọng:

```text
java.lang.Object
└── java.lang.Throwable
    ├── java.lang.Error
    │   ├── AssertionError
    │   ├── LinkageError
    │   └── VirtualMachineError
    │       └── OutOfMemoryError
    └── java.lang.Exception
        ├── java.io.IOException
        │   ├── java.io.FileNotFoundException
        │   └── java.io.EOFException
        ├── java.sql.SQLException
        ├── java.lang.InterruptedException
        └── java.lang.RuntimeException
            ├── java.lang.IllegalArgumentException
            │   └── java.lang.NumberFormatException
            ├── java.lang.IllegalStateException
            ├── java.lang.NullPointerException
            └── java.lang.IndexOutOfBoundsException
                └── java.lang.ArrayIndexOutOfBoundsException
```

Đây là **một phần của cây**, không phải mọi loại ngoại lệ trong Java. Từ sơ đồ:

- `Throwable` là kiểu cha chung; `Exception` và `Error` là hai nhánh con khác nhau.
- `IOException` là cha của `FileNotFoundException`; một handler khai báo `IOException` có thể bắt `FileNotFoundException`.
- `IllegalArgumentException` là cha của `NumberFormatException`; một handler khai báo `IllegalArgumentException` có thể bắt `NumberFormatException`.
- `Exception` là cha gián tiếp của `RuntimeException`, nhưng **không phải cha của `Error`**.
- `RuntimeException` và các lớp con thường là **unchecked exceptions**. `Error` và các lớp con cũng là unchecked theo quy tắc của Java, nhưng chúng không nên được xem như lỗi nghiệp vụ để bắt và phục hồi thông thường.
- Các lớp ngoại lệ thuộc `Exception` nhưng **không** thuộc `RuntimeException`, chẳng hạn `IOException`, là **checked exceptions**: trình biên dịch thường buộc mã gọi phải `catch` hoặc khai báo `throws`.

Quan hệ cha/con này là quan hệ **kế thừa lớp (`extends`)**, không phải dựa vào tên giống nhau. Chẳng hạn `NumberFormatException` kế thừa `IllegalArgumentException`, dù hai tên có vẻ mô tả các nguyên nhân khác nhau.

## 2. Quy tắc khớp kiểu: `catch (Parent ex)` bắt được đối tượng con

Giả sử tại runtime đang có đối tượng:

```java
NumberFormatException ex = new NumberFormatException("abc");
```

Đối tượng đó đồng thời tương thích với các kiểu tham chiếu `NumberFormatException`, `IllegalArgumentException`, `RuntimeException`, `Exception`, `Throwable` và `Object`. Trong một mệnh đề `catch`, kiểu tham số phải là `Throwable` hoặc lớp con của nó, nên không thể viết `catch (Object ex)`.

Nếu một vùng `try` có:

```java
try {
    Integer.parseInt("abc");
} catch (IllegalArgumentException ex) {
    System.out.println("Bắt bằng kiểu cha");
}
```

thì chương trình in:

```text
Bắt bằng kiểu cha
```

`Integer.parseInt("abc")` ném `NumberFormatException`, và `NumberFormatException` **là một** `IllegalArgumentException`. Vì thế nhánh khai báo kiểu cha khớp. Các đoạn này là ví dụ có thể đặt vào thân phương thức, không phải tệp chương trình độc lập.

Có thể diễn đạt quan hệ theo hướng sau:

- Khi đối tượng ngoại lệ là **con** của kiểu `catch`, handler có thể bắt nó.
- Khi kiểu `catch` là **con** của đối tượng ngoại lệ được ném thuộc kiểu cha, handler đó **không** bắt đối tượng cha chỉ vì có cùng họ.
- Hai kiểu nằm ở **hai nhánh không có quan hệ kế thừa với nhau** thì một handler của kiểu này không tự bắt kiểu kia. Ví dụ: `IOException` không bắt `NumberFormatException`.

**Kiểu runtime mới là yếu tố quyết định khi ném:** kể cả nếu một tham chiếu có kiểu khai báo `Exception`, nếu đối tượng thực tế là `NumberFormatException` thì handler phù hợp nhất trong chuỗi `catch` hợp lệ vẫn là handler có thể bắt `NumberFormatException`.

## 3. Thứ tự các nhánh `catch`: cụ thể trước, tổng quát sau

Một ngoại lệ có thể tương thích với nhiều kiểu, chẳng hạn `FileNotFoundException` cũng thuộc `IOException` và `Exception`. Nhưng khi có nhiều nhánh `catch` áp dụng cho **cùng một vùng `try`**, Java xét chúng **theo thứ tự từ trên xuống**. Nhánh khớp đầu tiên là nhánh xử lý. Vì thế:

```java
try {
    // Có thể ném FileNotFoundException hoặc IOException khác.
} catch (java.io.FileNotFoundException ex) {
    // Xử lý riêng trường hợp không tìm thấy tệp.
} catch (java.io.IOException ex) {
    // Xử lý các IOException còn lại.
}
```

Đây là cấu trúc minh họa (phần `try` cần thao tác I/O thực tế thì mới có tác dụng).

Nếu đảo lại:

```java
try {
    // Có thể ném IOException.
} catch (java.io.IOException ex) {
    // Bắt luôn cả FileNotFoundException.
} catch (java.io.FileNotFoundException ex) {
    // Không thể đến được: lỗi biên dịch.
}
```

Đoạn trên **cố ý không biên dịch**: `FileNotFoundException` đã bị handler `IOException` phía trước bao phủ hoàn toàn. Điều tương tự xảy ra khi đặt `catch (IllegalArgumentException)` trước `catch (NumberFormatException)`.

**Quy tắc thực hành:** trong cùng một chuỗi `catch`, đặt lớp ngoại lệ con trước lớp cha. Các nhánh anh em không kế thừa lẫn nhau, như `NumberFormatException` và một lớp con khác của `IllegalArgumentException`, không bắt buộc thứ tự tương đối vì quan hệ kế thừa; nên sắp xếp theo cách dễ đọc.

Thứ tự `catch` không phải một cuộc thi tìm kiểu “cụ thể nhất” tự động. Java đảm bảo bạn không đặt handler bị che khuất phía sau, sau đó chạy theo thứ tự handler được viết.

## 4. Ví dụ Java 17+ chạy được: chọn đúng nhánh theo phân cấp

Lưu chương trình hoàn chỉnh sau thành tệp **`CatchHierarchyDemo.java`**.

```java
import java.io.FileNotFoundException;
import java.io.IOException;

public class CatchHierarchyDemo {

    // Ngoại lệ nghiệp vụ là lớp con của IllegalArgumentException.
    static class InvalidQuantityException extends IllegalArgumentException {
        InvalidQuantityException(String message) {
            super(message);
        }
    }

    static void execute(String scenario) throws IOException {
        switch (scenario) {
            case "quantity" ->
                    throw new InvalidQuantityException("quantity <= 0");
            case "format" ->
                    throw new NumberFormatException("abc");
            case "argument" ->
                    throw new IllegalArgumentException("invalid argument");
            case "file" ->
                    throw new FileNotFoundException("config.txt");
            case "io" ->
                    throw new IOException("broken stream");
            default ->
                    System.out.println("execute: OK");
        }
    }

    static void handle(String scenario) {
        System.out.println("CASE " + scenario);
        try {
            execute(scenario);
            System.out.println("after execute");
        } catch (InvalidQuantityException ex) {
            System.out.println("catch InvalidQuantityException: "
                    + ex.getMessage());
        } catch (NumberFormatException ex) {
            System.out.println("catch NumberFormatException: "
                    + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            System.out.println("catch IllegalArgumentException: "
                    + ex.getMessage());
        } catch (FileNotFoundException ex) {
            System.out.println("catch FileNotFoundException: "
                    + ex.getMessage());
        } catch (IOException ex) {
            System.out.println("catch IOException: "
                    + ex.getMessage());
        } finally {
            System.out.println("finally");
        }
    }

    public static void main(String[] args) {
        for (String scenario : new String[] {
                "ok", "quantity", "format", "argument", "file", "io"
        }) {
            handle(scenario);
        }
    }
}
```

Biên dịch và chạy bằng **JDK 17 trở lên**:

```bash
javac -encoding UTF-8 CatchHierarchyDemo.java
java CatchHierarchyDemo
```

**Stdout chính xác**:

```text
CASE ok
execute: OK
after execute
finally
CASE quantity
catch InvalidQuantityException: quantity <= 0
finally
CASE format
catch NumberFormatException: abc
finally
CASE argument
catch IllegalArgumentException: invalid argument
finally
CASE file
catch FileNotFoundException: config.txt
finally
CASE io
catch IOException: broken stream
finally
```

### Đọc kết quả theo quan hệ kế thừa

| Tình huống | Kiểu đối tượng được ném | Kiểu cha cũng có thể bắt | `catch` thực tế được chọn |
| --- | --- | --- | --- |
| `ok` | Không có ngoại lệ | Không áp dụng | Không có; chạy `after execute` |
| `quantity` | `InvalidQuantityException` | `IllegalArgumentException`, `RuntimeException`, `Exception` | `InvalidQuantityException` |
| `format` | `NumberFormatException` | `IllegalArgumentException`, `RuntimeException`, `Exception` | `NumberFormatException` |
| `argument` | `IllegalArgumentException` | `RuntimeException`, `Exception` | `IllegalArgumentException` |
| `file` | `FileNotFoundException` | `IOException`, `Exception` | `FileNotFoundException` |
| `io` | `IOException` | `Exception`, `Throwable` | `IOException` |

Lưu ý quan trọng:

1. Với `quantity`, `InvalidQuantityException` kế thừa `IllegalArgumentException`; nếu bỏ nhánh `catch (InvalidQuantityException)` thì nhánh `catch (IllegalArgumentException)` sẽ xử lý lỗi đó.
2. Với `format`, `NumberFormatException` cũng kế thừa `IllegalArgumentException`, nhưng được xử lý riêng vì handler con đứng trước.
3. Với `file`, `FileNotFoundException` là `IOException` đặc biệt. Handler riêng có thể hiển thị thông báo “không tìm thấy tệp”; handler `IOException` phía sau dành cho lỗi I/O khác.
4. Với `io`, handler `FileNotFoundException` **không thể** bắt một đối tượng `IOException` thông thường chỉ vì chúng thuộc cùng cây kế thừa.
5. `finally` chạy sau mọi tình huống trong chương trình này. Khi có ngoại lệ, dòng `after execute` không chạy vì lời gọi `execute` không hoàn tất bình thường.

Chương trình chủ động `throw` các ngoại lệ để đầu ra không phụ thuộc tệp thật, hệ điều hành hay định dạng thông điệp nội bộ của JDK.

## 5. Thứ tự tìm handler: vùng `try` trong cùng trước, rồi đến caller

Phân cấp kiểu chỉ là một nửa quy tắc. JVM trước hết xem **handler đang có hiệu lực tại điểm ném**. Nếu có `try-catch` lồng nhau, một handler rộng trong khối bên trong có thể xử lý lỗi **trước khi** handler cụ thể trong khối bên ngoài nhìn thấy nó.

Ví dụ minh họa, đặt trong thân phương thức:

```java
try {
    try {
        throw new NumberFormatException("x");
    } catch (IllegalArgumentException ex) {
        System.out.println("inner: IllegalArgumentException");
    }
} catch (NumberFormatException ex) {
    System.out.println("outer: NumberFormatException");
}
```

Output:

```text
inner: IllegalArgumentException
```

Mặc dù `catch` ngoài là kiểu cụ thể hơn, ngoại lệ đã được **handler bên trong** bắt và không ném tiếp. JVM không bỏ qua handler trong để tìm một handler kiểu con ở tầng ngoài. Điều này cũng có nghĩa một `catch (Exception)` quá rộng trong tầng dưới có thể che mất cơ hội xử lý chuyên biệt ở caller.

Đổi khối `catch` bên trong thành `catch (IllegalArgumentException ex) { throw ex; }` thì ngoại lệ tiếp tục lan ra ngoài. Khi đó, handler `NumberFormatException` của khối ngoài có thể bắt lại đối tượng gốc. Trong trường hợp này không tạo đối tượng ngoại lệ mới; `throw ex` là thao tác ném lại.

### Nhìn từ call stack

```text
main()
  └─ controller()
       └─ service()
            └─ repository()
                 └─ throw FileNotFoundException
                      ↓
            service(): không có catch
                      ↓
       controller(): catch(IOException)
                      ↓
                 xử lý và dừng lan truyền
```

Handler `IOException` ở controller có thể bắt `FileNotFoundException` vì quan hệ cha/con. Nếu service đã bắt bằng `catch (Exception)` và không ném lại, controller không nhận được ngoại lệ, dù controller có handler cho `FileNotFoundException`.

## 6. Khác biệt giữa bắt `Exception`, `RuntimeException` và `Throwable`

Các nhánh sau có độ phủ rất khác nhau:

| Handler | Có thể bắt | Không bắt |
| --- | --- | --- |
| `catch (NumberFormatException ex)` | `NumberFormatException` và lớp con của nó nếu có | `IllegalArgumentException` thông thường, `IOException` |
| `catch (IllegalArgumentException ex)` | `IllegalArgumentException` và lớp con, gồm `NumberFormatException` | `IOException`, `IllegalStateException` |
| `catch (RuntimeException ex)` | Các ngoại lệ thuộc `RuntimeException` | `IOException` và các checked exception khác; `Error` |
| `catch (IOException ex)` | `IOException` và lớp con như `FileNotFoundException` | `RuntimeException`, `Error` |
| `catch (Exception ex)` | Checked exception thuộc `Exception` cùng các `RuntimeException` | Các loại `Error` |
| `catch (Throwable ex)` | `Exception` lẫn `Error` và mọi subclass của `Throwable` | Không có đối tượng `Throwable` nào ở ngoài phạm vi kiểu này |

**Không nên mặc định bắt `Throwable`** ở luồng xử lý nghiệp vụ. Nó có thể bắt cả `OutOfMemoryError` và `LinkageError`, những tình trạng mà ứng dụng có thể không đủ điều kiện tiếp tục an toàn. `catch (Exception)` đôi khi có ích ở ranh giới tác vụ để ghi nhận lỗi, nhưng ở tầng thấp thường quá rộng, có nguy cơ che mất lỗi lập trình.

Cũng cần phân biệt:

- `catch (RuntimeException)` **không** bắt `IOException` chỉ vì ngoại lệ cùng được ném bằng `throw`.
- `catch (Exception)` **có** bắt `NullPointerException` vì `NullPointerException` là hậu duệ của `RuntimeException` → `Exception`.
- `catch (Exception)` **không** bắt `OutOfMemoryError`; `Error` không kế thừa `Exception`.
- Những ngoại lệ runtime do lỗi lập trình có thể lan truyền mà không cần khai báo `throws`, nhưng điều đó không làm chúng mất đi quan hệ kế thừa.

## 7. Multi-catch: bắt nhiều kiểu anh em bằng một handler

Từ Java 7, có thể sử dụng dấu `|` khi nhiều ngoại lệ cần cùng cách xử lý:

```java
try {
    // Thao tác có thể ném IOException hoặc NumberFormatException.
} catch (java.io.IOException | NumberFormatException ex) {
    System.out.println("Không hoàn thành thao tác: "
            + ex.getClass().getSimpleName());
}
```

Đây là cấu trúc minh họa; phần `try` cần có lời gọi phù hợp. Các nhánh trong multi-catch phải **không có quan hệ cha/con với nhau**, để tránh một kiểu bị kiểu còn lại che phủ:

```java
// Sai — cố ý không biên dịch:
catch (NumberFormatException | IllegalArgumentException ex) {
    // NumberFormatException đã là IllegalArgumentException.
}
```

Lý do tương tự khi viết:

```java
// Sai — cố ý không biên dịch:
catch (java.io.IOException | java.io.FileNotFoundException ex) {
    // FileNotFoundException đã là IOException.
}
```

Trong một multi-catch, biến `ex` **ngầm là final**, không được gán sang một ngoại lệ khác. Kiểu tĩnh dùng cho các thao tác trên `ex` được suy ra từ các kiểu lựa chọn và quan hệ kiểu phù hợp; vì vậy không nên giả định có thể gọi phương thức riêng của chỉ một nhánh mà không kiểm tra kiểu.

Multi-catch phù hợp khi nhiều nguyên nhân có **cùng phản ứng thực sự**. Nếu lỗi nhập số cần yêu cầu người dùng sửa còn lỗi tệp cần báo lỗi vận hành, nên dùng các handler riêng.

## 8. Checked và unchecked ảnh hưởng gì tới `catch`?

Phân cấp kế thừa và quy tắc kiểm tra ngoại lệ lúc biên dịch có liên quan nhưng không giống nhau.

### Checked exception

Ví dụ `IOException` và `FileNotFoundException` là checked exceptions. Nếu một phương thức gọi API có thể ném `IOException`, nó phải bắt hoặc khai báo kiểu phù hợp:

```java
static String load(java.nio.file.Path path)
        throws java.io.IOException {
    return java.nio.file.Files.readString(path);
}
```

Phương thức minh họa này không độc lập; đoạn code không phụ thuộc sự hiện diện của một tệp cụ thể. Người gọi có thể:

```java
try {
    String text = load(path);
    // Dùng text khi đọc thành công.
} catch (java.io.IOException ex) {
    // Xử lý lỗi I/O và cả các lớp con được ném từ load.
}
```

Đoạn gọi này cần nằm trong phạm vi có biến `path` kiểu `java.nio.file.Path`. `catch (IOException)` là đủ cho `FileNotFoundException` và các lớp con của `IOException`. Không cần `catch` từng lớp con chỉ để thỏa yêu cầu trình biên dịch.

### Unchecked exception

`NumberFormatException`, `IllegalArgumentException`, `IllegalStateException` là unchecked exceptions. Một lời gọi có thể ném chúng mà phương thức gọi không bắt hoặc khai báo. **Nếu bạn muốn xử lý riêng, quy tắc quan hệ cha/con khi chọn handler vẫn áp dụng y hệt.**

Một thiết kế API không nhất thiết tốt hơn khi mọi lỗi đều chuyển thành `RuntimeException`. Quyết định checked/unchecked cần dựa vào hợp đồng API, khả năng phục hồi và cách các tầng sử dụng lỗi, chứ không chỉ để giảm số chữ `throws`.

### Về việc bắt rồi ném lại

Một `catch` có thể ném lại cùng đối tượng bằng `throw ex;` hoặc bọc vào loại ngoại lệ phù hợp. Đối với checked exceptions, chữ ký phương thức vẫn phải thỏa quy tắc của trình biên dịch. Trong một số trường hợp Java phân tích kiểu chính xác khi ném lại biến `catch` không bị gán lại (*precise rethrow*), nhưng không nên dựa vào nhận định rằng mọi `catch (Exception ex) { throw ex; }` đều có thể khai báo tùy ý một kiểu checked hẹp.

## 9. Tạo ngoại lệ riêng: chọn kiểu cha phù hợp

Khi ứng dụng có một loại lỗi riêng mà người gọi cần phân biệt, có thể tạo ngoại lệ kế thừa một lớp có sẵn:

```java
public class InvalidOrderException extends IllegalArgumentException {
    public InvalidOrderException(String message) {
        super(message);
    }

    public InvalidOrderException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

Đây là **một lớp độc lập** nếu lưu dưới tên `InvalidOrderException.java`; để dùng trong chương trình khác, cần đặt cùng package hoặc import thích hợp. Việc kế thừa `IllegalArgumentException` có ý nghĩa nếu lỗi cho biết đối số của lời gọi không hợp lệ. Một lỗi trạng thái nội bộ (ví dụ thao tác gửi khi đơn chưa được khởi tạo) có thể phù hợp hơn với `IllegalStateException`; lỗi I/O có thể phù hợp với `IOException` hoặc subclass của nó.

Các quyết định thiết kế:

1. **Chọn cha theo ngữ nghĩa:** đừng kế thừa `IllegalArgumentException` chỉ để tránh checked exception, nếu bản chất thất bại là lỗi môi trường bên ngoài.
2. **Giữ nguyên nhân gốc:** constructor nhận `Throwable cause` giúp bảo toàn nguyên nhân khi đổi loại ngoại lệ. Không thêm constructor chỉ vì thói quen nếu ứng dụng không dùng.
3. **Tận dụng đa hình:** handler `catch (IllegalArgumentException)` tự động bắt `InvalidOrderException`. Nếu cần xử lý riêng, đặt `catch (InvalidOrderException)` trước.
4. **Tránh phân cấp quá sâu:** nếu nhiều loại lỗi chỉ khác chuỗi thông điệp nhưng xử lý giống nhau, một loại ngoại lệ rõ ràng có thể đủ.
5. **Tài liệu hóa hợp đồng:** ghi các trường hợp ném ngoại lệ bằng `@throws` trong JavaDoc, đặc biệt khi caller phải biết nhánh nào cần phục hồi.

Không thể cho một lớp Java `extends` đồng thời hai lớp ngoại lệ. Nếu cần mô tả nhiều đặc điểm của lỗi, cân nhắc dữ liệu/thuộc tính của ngoại lệ, nguyên nhân gốc hoặc kiểu kết quả có cấu trúc, thay vì một cây kế thừa khó hiểu.

## 10. Chọn handler theo hành động cần làm

Bắt loại cụ thể nhất có ích khi mỗi loại dẫn đến **hành động khác nhau**, không chỉ để in tên lớp:

```java
try {
    // Lời gọi xử lý đầu vào và truy cập dữ liệu.
} catch (NumberFormatException ex) {
    // Thông báo định dạng số sai: người dùng cần sửa đầu vào.
} catch (IllegalArgumentException ex) {
    // Phản hồi tham số không hợp lệ theo hợp đồng ứng dụng.
} catch (java.io.FileNotFoundException ex) {
    // Xử lý hoặc báo thiếu tài nguyên cụ thể.
} catch (java.io.IOException ex) {
    // Báo lỗi I/O hoặc thử lại nếu chính sách cho phép.
}
```

Đây là cấu trúc minh họa; đoạn `try` cần chứa thao tác thực tế có thể ném những kiểu đã liệt kê. Không phải mọi API đều cần bốn nhánh như trên. Nếu phương thức chỉ có thể ném một loại lỗi và mọi người gọi phản ứng như nhau, việc thêm nhiều `catch` không đem lại lợi ích.

Các nguyên tắc giúp tránh bắt sai:

- **Giữ vùng `try` vừa đủ:** nếu gộp cả parse dữ liệu và xử lý nghiệp vụ vào một `try` rộng, `catch (IllegalArgumentException)` có thể vô tình bắt ngoại lệ do một lỗi lập trình ở bước khác.
- **Đừng bắt rồi bỏ qua:** `catch (Exception ignored) {}` che giấu lỗi và làm caller tưởng thao tác thành công.
- **Đừng bắt chỉ để ném y nguyên mà không thêm giá trị:** một `catch` chỉ có `throw ex;` thường là thừa, trừ khi cần một hành động như dọn dẹp hoặc ghi nhận có chủ đích.
- **Chỉ bọc lỗi khi có lý do:** chuyển `IOException` thành `IllegalStateException` có thể hợp lý ở một API không cho phép phục hồi I/O; khi bọc, truyền `cause` để không mất dấu.
- **Đừng xử lý theo chuỗi thông điệp:** không phân nhánh bằng `ex.getMessage().contains("file")`; thông điệp có thể thay đổi, được bản địa hóa hoặc chứa dữ liệu phụ.
- **Cân nhắc hợp đồng của tầng ngoài:** API có thể chuyển lỗi cụ thể thành mã HTTP phù hợp; tầng lưu trữ có thể giữ lại `IOException` cho tầng ứng dụng quyết định. Đây là quyết định thiết kế, không có một loại `catch` đúng cho mọi hệ thống.

## 11. Những lỗi biên dịch và lỗi tư duy thường gặp

| Sai lầm | Tại sao sai | Cách sửa |
| --- | --- | --- |
| Đặt `catch (Exception)` trước `catch (IOException)` | Handler con bị che khuất | Đưa `IOException` lên trước hoặc bỏ handler dư |
| Viết `catch (IOException | FileNotFoundException)` | Hai lựa chọn có quan hệ cha/con | Dùng `catch (IOException)` hoặc tách nhánh riêng |
| Giả định `catch (IOException)` bắt `IllegalStateException` | Hai loại ở nhánh khác nhau | Chọn kiểu theo ngoại lệ thực tế và chính sách xử lý |
| Nghĩ `catch (Exception)` bắt cả `Error` | `Error` là nhánh anh em của `Exception` | Không xem `Error` là lỗi nghiệp vụ thông thường |
| Tin rằng `catch` ngoài “cụ thể hơn” sẽ được ưu tiên | `try-catch` trong có thể xử lý trước | Xác định vùng xử lý đang hoạt động gần điểm ném |
| Gộp mọi lỗi vào `catch (Throwable)` rồi trả thành công | Che lấp lỗi nghiêm trọng và tạo trạng thái sai | Bắt các ngoại lệ có hành động xử lý có ý nghĩa |
| Xác định lỗi bằng `getMessage()` | Thông điệp không phải hợp đồng phân loại kiểu | Dùng kiểu ngoại lệ, thuộc tính hoặc kết quả tường minh |
| Tạo nhiều subclass nhưng không có xử lý phân biệt | API phức tạp mà ít lợi ích | Duy trì phân cấp tối giản dựa theo ngữ nghĩa |

## 12. Bài tập tự kiểm tra

**Tình huống A:** Một phương thức ném `NumberFormatException`. Trong cùng `try` chỉ có `catch (RuntimeException)`. Có bắt được không?

**Có.** `NumberFormatException` là con của `IllegalArgumentException`, mà `IllegalArgumentException` là con của `RuntimeException`.

**Tình huống B:** Có `catch (IOException)` rồi `catch (FileNotFoundException)`. Có biên dịch không?

**Không.** `FileNotFoundException` là subclass của `IOException` và đã được nhánh trước bắt hết.

**Tình huống C:** Một `try` bên trong bắt `IllegalArgumentException` rồi xử lý xong `NumberFormatException` mà không ném lại. `catch (NumberFormatException)` ở `try` ngoài có chạy không?

**Không.** Ngoại lệ đã được xử lý tại phạm vi trong, nên không lan ra ngoài.

**Tình huống D:** Có `catch (Exception)` thì một `OutOfMemoryError` có bị bắt không?

**Không.** `OutOfMemoryError` kế thừa `Error`, còn `Error` không kế thừa `Exception`.

**Tình huống E:** Muốn chung một thông báo cho lỗi đọc tệp và nhập số, có thể viết `catch (IOException | NumberFormatException)` không?

**Có**, nếu cả hai có thể phát sinh trong `try` đang xét. Chúng không có quan hệ cha/con và multi-catch cho phép chung một handler.

**Tình huống F:** Tạo `InvalidOrderException extends IllegalArgumentException`. Nếu xóa handler dành riêng cho `InvalidOrderException`, handler `IllegalArgumentException` phía sau có bắt được không?

**Có.** Handler cha bắt được đối tượng con, miễn là ngoại lệ thực sự truyền đến nó mà chưa được xử lý ở `try` bên trong.

**Ghi nhớ:** Java chọn `catch` bằng **kiểu runtime và quan hệ kế thừa**, đồng thời tuân theo **phạm vi handler đang hoạt động và thứ tự các nhánh trong cùng một `try`**. Hãy đặt handler cụ thể trước handler cha và chỉ bắt ở tầng có hành động xử lý phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-hierarchy-008">Vì sao catch Exception không bắt mọi Throwable</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao `catch (Exception)` không bắt mọi `Throwable` trong Java?

> **Chủ đề:** Java Core — Exception · Phân cấp ngoại lệ. Cốt lõi của câu trả lời: **`Exception` chỉ là một nhánh con của `Throwable`.** `catch (Exception e)` xử lý các đối tượng có kiểu `Exception` hoặc lớp con, còn những đối tượng thuộc nhánh `Error` (và lớp con trực tiếp khác của `Throwable`) không phải là `Exception` nên không khớp. Việc Java cho phép `catch (Throwable)` không có nghĩa ứng dụng nên bắt mọi lỗi ở cấp đó.

## 1. Nhìn đúng cây kế thừa

`Throwable` là lớp gốc của các đối tượng có thể được ném qua `throw` và được bắt bằng `catch` theo hệ thống ngoại lệ Java. Hai nhánh quen thuộc bên dưới nó là `Exception` và `Error`:

```text
java.lang.Object
└── java.lang.Throwable
    ├── java.lang.Exception
    │   ├── java.io.IOException
    │   │   └── java.io.FileNotFoundException
    │   └── java.lang.RuntimeException
    │       ├── java.lang.IllegalArgumentException
    │       │   └── java.lang.NumberFormatException
    │       ├── java.lang.NullPointerException
    │       └── java.lang.IllegalStateException
    │
    ├── java.lang.Error
    │   ├── java.lang.AssertionError
    │   ├── java.lang.LinkageError
    │   └── java.lang.VirtualMachineError
    │       ├── java.lang.OutOfMemoryError
    │       └── java.lang.StackOverflowError
    │
    └── (các lớp khác nếu tự extends Throwable)
```

Cây trên liệt kê **các ví dụ tiêu biểu**, không phải toàn bộ lớp con trong JDK. Sự phân nhánh có nghĩa:

- `Exception extends Throwable`: mọi đối tượng `Exception` đều là `Throwable`.
- `RuntimeException extends Exception`: mọi `RuntimeException` đều là `Exception` và `Throwable`.
- `Error extends Throwable`: mọi đối tượng `Error` đều là `Throwable`, **nhưng không thuộc kiểu `Exception`**.
- Java cho phép viết một lớp trực tiếp `extends Throwable`. Lớp đó cũng không tự trở thành `Exception` hay `Error`. Đây là khả năng của ngôn ngữ, tuy nhiên thường nên chọn một nhánh chuẩn khi thiết kế API.

**Quan hệ quyết định:** `Exception` và `Error` là hai nhánh anh em, không phải quan hệ cha–con. `catch (Exception)` không có nhiệm vụ “bắt tất cả thứ có thể ném”; nó chỉ bắt những đối tượng tương thích với kiểu `Exception`.

## 2. Java chọn một `catch` bằng cách nào?

Khi một exception hoặc error được ném bên trong `try`, runtime kiểm tra các `catch` kèm theo khối đó theo thứ tự khai báo để tìm handler có kiểu tương thích với **đối tượng thực tế đang được ném**.

Một handler `catch (T e)` có thể xử lý đối tượng có kiểu runtime `T` hoặc một lớp con của `T`. Trong thực tế còn có multi-catch như `catch (IOException | IllegalArgumentException e)`, khi đối tượng khớp một trong các kiểu được liệt kê.

Giả sử đối tượng đang được ném là `NumberFormatException`:

- `catch (NumberFormatException e)` khớp chính xác.
- `catch (IllegalArgumentException e)` khớp vì `NumberFormatException` là lớp con của `IllegalArgumentException`.
- `catch (RuntimeException e)` khớp vì đây là một ancestor của nó.
- `catch (Exception e)` khớp vì `RuntimeException extends Exception`.
- `catch (Throwable e)` cũng khớp vì `Throwable` là tổ tiên chung.
- `catch (Error e)` **không khớp**.

Nếu đối tượng là `AssertionError`:

- `catch (AssertionError e)` khớp.
- `catch (Error e)` khớp.
- `catch (Throwable e)` khớp.
- `catch (Exception e)` **không khớp**, vì `AssertionError` nằm dưới `Error`.

### 2.1. Bảng đối chiếu nhanh

| Đối tượng được ném | `catch (Exception)` | `catch (RuntimeException)` | `catch (Error)` | `catch (Throwable)` |
| --- | --- | --- | --- | --- |
| `new IOException("...")` | Có | Không | Không | Có |
| `new NumberFormatException("...")` | Có | Có | Không | Có |
| `new NullPointerException("...")` | Có | Có | Không | Có |
| `new AssertionError("...")` | **Không** | Không | Có | Có |
| `new LinkageError("...")` | **Không** | Không | Có | Có |
| Đối tượng từ lớp trực tiếp `extends Throwable` | **Không** | Không | Không | Có |

Ở đây “có” nghĩa là **tương thích về kiểu**, không ngụ ý handler đó chắc chắn được chạy trong mọi đoạn code: một handler đứng trước có thể đã bắt đối tượng; hoặc exception có thể nằm ở khối `try` khác. Ngoài ra, Java có quy tắc biên dịch về handler không thể tới được (unreachable catch), nên không phải mọi cách xếp các nhánh trên đều là chương trình hợp lệ.

### 2.2. Thứ tự handler khi nhiều `catch` cùng khớp

Trong **cùng một `try`**, Java xét các `catch` theo thứ tự khai báo và chọn nhánh khớp đầu tiên. Do đó, `catch (NumberFormatException)` phải đứng **trước** `catch (Exception)`: nếu đảo ngược, compiler báo handler kiểu con không thể tới được.

```java
try {
    Integer.parseInt("abc");
} catch (NumberFormatException e) {
    System.out.println("Chuỗi không phải số");
} catch (Exception e) {
    System.out.println("Exception khác");
}
```

Đây là mảnh mã đặt trong phương thức; nó in `Chuỗi không phải số`. Thứ tự handler giải thích **handler nào được chọn trong nhánh Exception**; câu hỏi trọng tâm ở đây là giới hạn kiểu: dù đặt ở đâu, `catch (Exception)` vẫn không khớp với `Error` vì `Error` không kế thừa `Exception`.

## 3. Chương trình Java 17: `catch (Exception)` bị `AssertionError` đi qua

Đây là ví dụ độc lập, minh họa hai tình huống bằng cách tách handler ở tầng trong và ngoài. Để quan sát an toàn, dùng `AssertionError` do chương trình **chủ động ném**. Không tạo `OutOfMemoryError`/`StackOverflowError` thực để thử nghiệm.

Lưu thành `CatchExceptionBoundaryDemo.java`:

```java
public class CatchExceptionBoundaryDemo {
    static void runCase(String label, Runnable operation) {
        try {
            try {
                operation.run();
                System.out.println(label + ": thành công");
            } catch (Exception e) {
                System.out.println(label + ": catch Exception -> "
                        + e.getClass().getSimpleName());
            }
        } catch (AssertionError e) {
            System.out.println(label + ": catch AssertionError bên ngoài -> "
                    + e.getClass().getSimpleName());
        } finally {
            System.out.println(label + ": finally");
        }
    }

    public static void main(String[] args) {
        runCase("Parse", () -> {
            Integer.parseInt("abc");
        });

        runCase("Kiểm tra", () -> {
            throw new AssertionError("Chủ động tạo để minh họa");
        });
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 CatchExceptionBoundaryDemo.java
java CatchExceptionBoundaryDemo
```

**Stdout chính xác:**

```text
Parse: catch Exception -> NumberFormatException
Parse: finally
Kiểm tra: catch AssertionError bên ngoài -> AssertionError
Kiểm tra: finally
```

**Giải thích từng đường đi:**

1. `Integer.parseInt("abc")` ném `NumberFormatException`, là lớp con của `RuntimeException` → `Exception`. Handler `catch (Exception)` ở khối trong nhận được nó, nên không cần tìm handler ngoài. Sau đó chạy `finally`.
2. `throw new AssertionError(...)` ném đối tượng thuộc nhánh `Error`. Handler `catch (Exception)` không tương thích, nên exception lan ra khỏi khối trong và được handler `catch (AssertionError)` ở khối ngoài bắt. `finally` của khối ngoài vẫn chạy theo đường điều khiển bình thường của Java.
3. Câu lệnh in `"...: thành công"` không chạy ở cả hai nhánh vì `operation.run()` đều kết thúc bằng ném một `Throwable`.

**Điểm quan trọng:** ví dụ chủ động bắt `AssertionError` **chỉ để chứng minh cơ chế matching**. Nó không khuyến khích ứng dụng bắt lỗi nhánh `Error` như một chính sách phục hồi thông thường.

### 3.1. Điều gì xảy ra nếu không có `catch (AssertionError)` bên ngoài?

Nếu xóa handler ngoài và để `AssertionError` không được xử lý ở tầng cao hơn, lỗi sẽ tiếp tục lan qua các phương thức gọi. Nếu một `Throwable` không được bắt tới ranh giới đầu của thread, handler uncaught exception của thread có thể báo stack trace hoặc xử lý theo cấu hình. Không thể dự đoán mọi dòng in của một stack trace như một stdout ổn định: vị trí line number thay đổi theo file, lỗi thường được báo qua stderr, và handler có thể được cấu hình.

Điều đáng nhớ là **`catch (Exception)` không biến lỗi `Error` thành kết quả hợp lệ**, ngay cả khi trong cùng method có `finally`.

## 4. `Exception` không đồng nghĩa với “checked exception”

Một nhầm lẫn phổ biến khác là nghĩ `catch (Exception)` chỉ bắt checked exception, còn `RuntimeException` là “unchecked nên không bắt được”. Điều này sai.

**Checked/unchecked** là cách Java compiler quản lý nghĩa vụ bắt hoặc khai báo lỗi, khác với việc runtime so kiểu handler:

- `Exception` và các lớp con của nó, **trừ `RuntimeException` và các lớp con**, thường là checked exceptions.
- `RuntimeException` và các lớp con là unchecked, nhưng **vẫn nằm trong nhánh `Exception`**, nên `catch (Exception)` bắt được.
- `Error` và các lớp con là unchecked, nhưng **không nằm trong nhánh `Exception`**, nên `catch (Exception)` không bắt được.
- Một lớp tự tạo `extends Throwable` trực tiếp (ngoài `RuntimeException` và `Error`) thuộc loại checked theo quy tắc của ngôn ngữ, nhưng `catch (Exception)` vẫn không bắt được.

Nói chính xác hơn, một lớp exception là **unchecked** nếu nó là lớp con của `RuntimeException` hoặc `Error`; các lớp throwable còn lại là **checked**. Do đó “checked” không phải tên gọi của toàn bộ và chỉ riêng nhánh `Exception`.

### 4.1. Ví dụ phân loại có thể chạy

Tạo `ThrowableTypeDemo.java`:

```java
import java.io.IOException;

public class ThrowableTypeDemo {
    static final class CustomSignal extends Throwable {
        CustomSignal(String message) {
            super(message);
        }
    }

    static void describe(Throwable value) {
        System.out.println(value.getClass().getSimpleName()
                + " | Exception=" + (value instanceof Exception)
                + " | Error=" + (value instanceof Error));
    }

    public static void main(String[] args) {
        describe(new IOException("I/O"));
        describe(new NumberFormatException("parse"));
        describe(new AssertionError("assert"));
        describe(new CustomSignal("direct Throwable child"));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 ThrowableTypeDemo.java
java ThrowableTypeDemo
```

Stdout:

```text
IOException | Exception=true | Error=false
NumberFormatException | Exception=true | Error=false
AssertionError | Exception=false | Error=true
CustomSignal | Exception=false | Error=false
```

Phương thức `describe(Throwable value)` nhận được tất cả đối tượng vì tham số kiểu `Throwable`. Tuy nhiên truyền đối tượng vào tham số **khác** với dùng `catch`: trong ví dụ này, chương trình chỉ kiểm tra kiểu bằng `instanceof`, không hề ném chúng.

Lớp `CustomSignal extends Throwable` được đưa vào để chứng minh cây kế thừa có thể có các nhánh khác. Vì nó không thuộc `RuntimeException` hay `Error`, nếu method thực sự `throw new CustomSignal(...)` thì compiler sẽ yêu cầu bắt hoặc khai báo kiểu này. Trong thiết kế Java thông thường, nên kế thừa `Exception` hoặc `RuntimeException` tùy hợp đồng thay vì trực tiếp từ `Throwable`.

## 5. Vì sao lại có hai nhánh `Exception` và `Error`?

Sự tách biệt giúp biểu đạt **loại tình huống mà code ứng dụng thường có thể xử lý** so với vấn đề có thể nằm ở cơ chế thực thi, liên kết lớp, máy ảo hoặc lỗi giả định chương trình.

### 5.1. Nhánh `Exception`

Ví dụ:

- `IOException`: đọc/ghi dữ liệu thất bại, file không mở được, stream gặp lỗi.
- `FileNotFoundException`: trường hợp cụ thể dưới `IOException`.
- `NumberFormatException`: chuỗi không thể chuyển thành số yêu cầu.
- `IllegalArgumentException`: tham số không hợp lệ theo API.
- `IllegalStateException`: trạng thái hiện tại không cho phép thao tác.
- `NullPointerException`: thao tác không hợp lệ với tham chiếu `null`, thường là lỗi lập trình hoặc vi phạm hợp đồng.

Một số có phương án xử lý hợp lý: yêu cầu người dùng nhập lại, chọn file khác, hủy thao tác hiện tại hoặc chuyển thành thông báo lỗi đúng ngữ cảnh. Nhưng không phải mọi đối tượng `Exception` đều “nhẹ” hay chắc chắn phục hồi được. Ví dụ `NullPointerException` nhiều khi chỉ ra bug cần sửa; bắt rồi tiếp tục vô điều kiện có thể làm dữ liệu sai nặng hơn.

### 5.2. Nhánh `Error`

Ví dụ:

- `OutOfMemoryError`: JVM không đủ bộ nhớ cho yêu cầu cấp phát hoặc giới hạn bộ nhớ liên quan.
- `StackOverflowError`: luồng gọi quá sâu làm tràn stack.
- `LinkageError`: có vấn đề trong liên kết/tải/kiểm tra tương thích của lớp.
- `AssertionError`: một assertion thất bại, hoặc code chủ động ném đối tượng này.
- `ThreadDeath`: cũng là một `Error` trong Java, liên quan đến cơ chế dừng thread đã lỗi thời.

Nhiều `Error` phản ánh tình trạng môi trường hoặc giả định nền tảng bị phá vỡ, nơi tiếp tục xử lý bình thường có thể thiếu an toàn. Tuy nhiên **không nên khẳng định mọi `Error` đều không thể bắt được hoặc luôn kết thúc toàn bộ JVM**. Chúng vẫn là đối tượng `Throwable` có thể được handler tương thích bắt ở cấp ngôn ngữ. Một `AssertionError` do test tạo là ví dụ dễ bắt trong môi trường kiểm thử. Vấn đề là **có nên phục hồi hay không**, tùy loại lỗi và ranh giới xử lý.

Java không bảo đảm rằng sau `OutOfMemoryError`, chương trình có đủ tài nguyên để log, tạo exception mới, đóng mọi resource hoặc chạy logic phục hồi phức tạp. Vì vậy không nên viết chương trình dựa trên niềm tin rằng `catch (Throwable)` giúp “không bao giờ crash”.

## 6. So sánh `catch (Exception)`, `catch (Error)` và `catch (Throwable)`

| Handler | Bao phủ về kiểu | Cách dùng điển hình | Rủi ro nếu lạm dụng |
| --- | --- | --- | --- |
| `catch (NumberFormatException e)` | Một loại lỗi parse và lớp con | Ở ranh giới nhập dữ liệu, khi có cách giải thích hoặc yêu cầu nhập lại | Chỉ hợp lý nếu có hướng xử lý cụ thể. |
| `catch (IOException e)` | Lỗi I/O và lớp con | Thêm ngữ cảnh, chọn nguồn khác, báo thất bại thao tác | Nuốt lỗi có thể khiến caller tưởng đã hoàn tất. |
| `catch (Exception e)` | Mọi `Exception` và lớp con, gồm checked và unchecked | Ranh giới ứng dụng có chính sách xử lý đa dạng; đôi lúc adapter chuyển đổi lỗi | Che lỗi lập trình nếu bắt quá rộng rồi im lặng. **Không bắt `Error`.** |
| `catch (RuntimeException e)` | RuntimeException và lớp con | Ranh giới chịu trách nhiệm cách ly một số lỗi không dự kiến | Có thể che bug và làm hỏng trạng thái nghiệp vụ. |
| `catch (Error e)` | `Error` và lớp con | Tình huống rất đặc thù, ví dụ kiểm thử một `AssertionError` chủ động tạo | Cố phục hồi từ lỗi VM/linkage có thể nguy hiểm hoặc bất khả thi. |
| `catch (Throwable t)` | Cả nhánh `Exception`, `Error` và các lớp con khác | Công cụ chẩn đoán/framework cấp rất thấp, với chính sách giữ nguyên và lan truyền lỗi nghiêm trọng | Nuốt lỗi nặng, sai trạng thái, biến hệ thống hỏng thành “thành công”. |

**Quy tắc thực dụng:** bắt loại cụ thể nhất mà tầng hiện tại thực sự biết xử lý. Nếu không thể phục hồi, có thể để exception lan lên, thêm ngữ cảnh khi chuyển tầng và giữ nguyên `cause`. “Bắt rộng” là lựa chọn thiết kế ở ranh giới kiểm soát chặt, không phải mẹo để mọi method an toàn.

### 6.1. Một `catch (Throwable)` có ngăn tất cả lỗi trên thế giới?

Không. Đây là handler rộng nhất về **kiểu đối tượng Java có thể ném**, nhưng nó vẫn có giới hạn:

1. Chỉ bắt `Throwable` được ném trong phạm vi `try` tương ứng và truyền qua luồng điều khiển tới handler đó. Ngoại lệ ở một **thread khác** không tự nhảy tới handler của thread hiện tại.
2. Không bảo đảm xử lý được tiến trình bị hệ điều hành chấm dứt, JVM dừng đột ngột, mất điện hoặc sự cố native làm sập tiến trình.
3. Có thể không đủ bộ nhớ/tài nguyên để thực thi handler một cách đáng tin sau lỗi nghiêm trọng.
4. Không biến side effect đã xảy ra thành thao tác có rollback.
5. Không tự phát hiện lỗi logic chỉ tạo kết quả sai nhưng **không ném** exception.

Đừng diễn giải `catch (Throwable)` như một “lá chắn tuyệt đối”.

### 6.2. Vì sao `catch (Exception)` đôi khi vẫn có mặt trong code production?

Ở biên giới của một request handler, task executor, CLI command hoặc job runner, ứng dụng có thể cần chuyển nhiều loại exception thành lỗi của tác vụ cụ thể. Ví dụ một tác vụ riêng thất bại, hệ thống ghi log và đánh dấu tác vụ thất bại rồi tiếp tục nhận công việc khác. Điều này chỉ hợp lý khi:

- phạm vi bắt và trạng thái cần phục hồi đã rõ;
- failure được báo thành failure, không giả như thành công;
- các tài nguyên của tác vụ được dọn đúng;
- lỗi nghiêm trọng ở nhánh `Error` không bị âm thầm nuốt;
- có chính sách về retry, giám sát và cách ly trạng thái đã hỏng.

Ngay cả ở những nơi đó, handler cụ thể hơn thường giúp quyết định phù hợp hơn so với một `catch (Exception)` chung.

## 7. Từ `throw` và `throws` đến handler: đừng nhầm lúc biên dịch với lúc chạy

Xét ví dụ phương thức:

```java
static void readData() throws java.io.IOException {
    throw new java.io.IOException("Không đọc được");
}
```

Đây là đoạn mã minh họa, không phải chương trình đầy đủ. Vì `IOException` là checked exception, một caller trực tiếp phải bắt hoặc khai báo tiếp. Có thể viết `catch (Exception)` để bắt vì `IOException extends Exception`, nhưng dùng `catch (IOException)` giúp diễn đạt ý định cụ thể.

Trong khi đó:

```java
static void detectInvalidState() {
    throw new AssertionError("Giả định chương trình bị phá vỡ");
}
```

Đoạn minh họa thứ hai ném `AssertionError`, là unchecked `Error`. Nó không buộc ghi `throws AssertionError`. Một caller viết `catch (Exception)` quanh lời gọi **vẫn không thể bắt `AssertionError`**. Quy tắc checked/unchecked của compiler và quan hệ kiểu tại runtime là **hai vấn đề khác nhau**.

### 7.1. Một method khai báo `throws Exception` có thể ném `Error` không?

Có. `throws Exception` là khai báo cho khả năng thoát của exception checked tương ứng; nó không cấm RuntimeException hoặc Error phát sinh khi chạy.

Ngược lại, việc một method khai báo `throws Throwable` không bắt buộc caller dùng `catch (Throwable)`. Caller có thể khai báo `throws Throwable`, hoặc bắt các loại phù hợp theo quy tắc checked exception. Trong thiết kế API thông thường, tránh khai báo quá tổng quát khi có thể mô tả lỗi cụ thể.

### 7.2. Compiler kiểm tra được gì?

Compiler kiểm tra các ràng buộc như handler đã bị che bởi handler trước, checked exception chưa bắt/khai báo, và một số handler checked không thể nhận ngoại lệ được ném từ khối `try`. Nhưng compiler không quyết định rằng một `Error` “sẽ không thể xảy ra” chỉ vì method không khai báo `throws Error`.

Không phải mọi đoạn code có `catch (Error)` đều là lựa chọn thiết kế tốt. Khả năng biên dịch không thay thế được đánh giá khả năng phục hồi.

## 8. Ngoại lệ bị bọc: handler nhìn **đối tượng được ném**, không tự chọn `cause`

Lỗi gốc có thể là `Error` nhưng code tầng ngoài ném một `Exception` mới chứa lỗi đó làm nguyên nhân. Khi ấy `catch (Exception)` bắt **wrapper**, vì đối tượng thực sự được ném chính là một `Exception`. Nó không tự “bắt trực tiếp `Error` bên trong”.

Tạo `WrappedThrowableDemo.java`:

```java
public class WrappedThrowableDemo {
    static void callService() {
        try {
            throw new AssertionError("Gốc");
        } catch (AssertionError original) {
            throw new IllegalStateException("Bọc lỗi để minh họa", original);
        }
    }

    public static void main(String[] args) {
        try {
            callService();
        } catch (Exception e) {
            System.out.println("Bắt được: "
                    + e.getClass().getSimpleName());
            System.out.println("Cause: "
                    + e.getCause().getClass().getSimpleName());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 WrappedThrowableDemo.java
java WrappedThrowableDemo
```

Stdout:

```text
Bắt được: IllegalStateException
Cause: AssertionError
```

Ví dụ dùng `AssertionError` để minh họa quan hệ wrapper/cause **chứ không phải mẫu xử lý Error nên áp dụng**. Trong ứng dụng thực tế, thường tránh chuyển mọi `Error` thành exception bình thường để tiếp tục chạy. Hợp đồng và chính sách ở tầng đó mới quyết định khi nào bọc một nguyên nhân là hợp lý.

Trường hợp đối xứng: một `Error` có `getCause()` là `IOException` thì `catch (Exception)` **không** bắt được đối tượng `Error` đang bay ra, dù nguyên nhân bên trong là checked exception. Handler so kiểu đối tượng **bên ngoài được ném**, không đào sâu `getCause()` để chọn catch.

## 9. `finally`, try-with-resources và quá trình lan truyền

Khi `catch (Exception)` không phù hợp với một `Error`, các handler ở các mức gọi phía trên vẫn có thể được xem xét. Trong quá trình ra khỏi khối, `finally` thông thường vẫn được thực hiện; try-with-resources cũng gọi `close()` của resource theo quy tắc.

Ví dụ:

```java
try {
    throw new AssertionError("Báo lỗi");
} catch (Exception e) {
    System.out.println("Không chạy");
} finally {
    System.out.println("Dọn dẹp");
}
```

Đây là đoạn mã minh họa trong method. Khi chạy, `catch (Exception)` không được chọn; `finally` in ra “Dọn dẹp”; sau đó `AssertionError` tiếp tục lan ra ngoài nếu chưa có handler ở tầng trên. Vì thế, một khối `finally` được thực hiện **không chứng minh** exception đã được xử lý.

Không nên tuyên bố `finally` chắc chắn chạy trong mọi tình huống; nếu process/JVM bị kết thúc bất thường, không có bảo đảm phổ quát. Không nên `return` hoặc ném lỗi mới trong `finally` vì có thể che lỗi gốc.

### 9.1. Tránh bỏ mất lỗi khi dọn dẹp

Nếu thân try-with-resources ném exception và `close()` cũng ném, Java thường giữ lỗi của thân khối làm exception chính; lỗi đóng được thêm vào `getSuppressed()`. Cơ chế này áp dụng theo quy tắc của try-with-resources, không phụ thuộc giả định “`catch (Exception)` sẽ bắt được mọi lỗi”.

Nếu exception chính là `Error`, handler `catch (Exception)` vẫn không xử lý được nó, dù một lỗi `IOException` từ `close()` đã được suppressed vào đối tượng chính. Nhánh loại lỗi được **ném ra ngoài** mới quyết định handler nào khớp.

## 10. Cần bắt `Error` khi nào?

Không có quy tắc “tuyệt đối không bao giờ bắt `Error`” trong ngôn ngữ Java. Một số môi trường chuyên biệt có thể cần nhận diện một lỗi cụ thể:

- Framework kiểm thử có thể ghi nhận `AssertionError` như test failure để báo cáo.
- Công cụ chẩn đoán hoặc ranh giới runtime rất thấp có thể quan sát lỗi để ghi thông tin tối thiểu rồi **ném tiếp**.
- Mã có giao thức cách ly và phục hồi rất cụ thể có thể xử lý một loại `Error` đặc thù; quyết định này cần hiểu sâu về API và trạng thái của hệ thống.

Nhưng với code nghiệp vụ thông thường, không nên:

```java
// Phản ví dụ: đừng dùng như chiến lược xử lý lỗi chung.
try {
    performBusinessOperation();
} catch (Throwable ignored) {
    // Nuốt mọi Throwable rồi tiếp tục như thể thành công.
}
```

Đoạn trên là **mã minh họa không độc lập**: `performBusinessOperation()` chưa được định nghĩa. Nó cho thấy anti-pattern cần tránh, không phải chương trình Java hoàn chỉnh.

Cách tốt hơn ở ranh giới xử lý ứng dụng thường là bắt đúng exception có kế hoạch phục hồi; dọn dẹp tài nguyên bằng try-with-resources; log hoặc truyền tiếp các thất bại không thể tự khôi phục; để lỗi nghiêm trọng được xử lý bằng chính sách vận hành/thử lại/khởi động lại phù hợp.

Đặc biệt, không cố tạo `OutOfMemoryError` hay `StackOverflowError` trên môi trường đang làm việc chỉ để kiểm tra bảng matching. Dùng `AssertionError` chủ động tạo là đủ để chứng minh quan hệ kế thừa và handler.

## 11. Những nhầm lẫn cần tránh

| Nhận định sai | Giải thích đúng |
| --- | --- |
| “`Throwable` và `Exception` là cùng một kiểu.” | `Exception` là **một lớp con** của `Throwable`; `Error` là nhánh khác. |
| “`catch (Exception)` bắt mọi thứ có thể được `throw`.” | Nó không bắt `Error` hoặc lớp con trực tiếp khác của `Throwable` nằm ngoài `Exception`. |
| “`RuntimeException` là unchecked nên `catch (Exception)` không bắt được.” | `RuntimeException extends Exception`; vẫn bắt được. |
| “`Error` không thể `catch`.” | Có thể bắt bằng kiểu tương thích (`Error`, `Throwable` hoặc lớp con cụ thể), nhưng không nhất thiết nên phục hồi. |
| “`catch (Throwable)` bảo đảm app không bao giờ dừng.” | Nó không bảo vệ khỏi dừng JVM/process, lỗi native, thiếu tài nguyên nghiêm trọng hay exception ở thread khác. |
| “Checked exception nghĩa là kế thừa trực tiếp `Exception`.” | Checked/unchecked tuân theo nhánh `RuntimeException`/`Error`; một lớp trực tiếp `extends Throwable` có thể là checked. |
| “`throws Exception` ngăn method ném `Error`.” | `throws` không áp đặt giới hạn đó lên unchecked `Error`. |
| “Một `Error` nằm trong `getCause()` sẽ làm `catch (Exception)` thất bại.” | Handler so với **wrapper đang được ném**; nếu wrapper là `Exception` thì catch vẫn khớp. |
| “Nếu `finally` chạy thì exception đã được bắt.” | `finally` thường chạy cả khi exception đang lan truyền; không phải handler. |
| “Bắt rộng rồi bỏ qua sẽ làm chương trình an toàn.” | Nó có thể che lỗi nghiêm trọng và để trạng thái hỏng tiếp tục được sử dụng. |
| “Handler kiểm tra kiểu ngoại lệ theo biến khai báo ban đầu.” | Handler chọn theo **kiểu thực tế của đối tượng được ném** và quan hệ kế thừa. |

## 12. Bài tập kiểm tra hiểu sâu

### Bài 1 — Dự đoán nhánh bắt mà không chạy chương trình

Với từng đối tượng sau, cho biết `catch (Exception)` có nhận không, rồi giải thích quan hệ kế thừa:

1. `new ArithmeticException("division")`.
2. `new java.io.EOFException("end")`.
3. `new AssertionError("broken invariant")`.
4. `new java.lang.NoClassDefFoundError("missing")`.
5. `new IllegalStateException("state")`.

**Đáp án:** (1) có, (2) có, (3) không, (4) không, (5) có. `ArithmeticException` và `IllegalStateException` là các lớp con của `RuntimeException` → `Exception`; `EOFException` thuộc nhánh `IOException` → `Exception`. `AssertionError` và `NoClassDefFoundError` thuộc nhánh `Error`.

### Bài 2 — Chỉnh thứ tự handler

Cho đoạn minh họa:

```java
try {
    Integer.parseInt("?");
} catch (Exception e) {
    System.out.println("Chung");
} catch (NumberFormatException e) {
    System.out.println("Riêng");
}
```

Tại sao không biên dịch? Hãy sửa bằng cách đảo nhánh cụ thể lên trước. Sau khi sửa, stdout là `Riêng`, bởi đối tượng được ném là `NumberFormatException` và handler đầu tiên tương thích đã nhận nó.

### Bài 3 — Bọc lỗi có thay đổi kết quả matching?

Trong `WrappedThrowableDemo`, thay `new IllegalStateException("...", original)` bằng `new AssertionError("...", original)`. Hỏi `catch (Exception)` ở `main` có nhận lỗi không? Vì sao?

**Gợi ý:** handler không tự mở `cause`. Lỗi được ném ở ngoài bây giờ là `AssertionError` (nhánh `Error`), vì vậy `catch (Exception)` không khớp.

### Bài 4 — Lỗi ở thread khác

Nếu `main` viết `try { new Thread(task).start(); } catch (Exception e) { ... }`, còn `task` ném một exception sau khi thread chạy, handler trong `main` có bắt được ngoại lệ đó không? **Không**, vì ngoại lệ xảy ra trên thread khác; handler của `main` chỉ áp dụng cho luồng điều khiển của thread hiện tại. Hãy tìm hiểu `Thread.UncaughtExceptionHandler` hoặc cơ chế thu lỗi qua `Future` khi học concurrency.

### Bài 5 — Thiết kế chính sách

Với ứng dụng xử lý file do người dùng chọn, quyết định tầng nào sẽ bắt `FileNotFoundException`/`IOException`, tầng nào thông báo cho người dùng, và bạn sẽ làm gì nếu gặp `OutOfMemoryError`. Nêu lý do tại sao không nên dùng `catch (Throwable)` rồi quay về thông báo “Đọc file thành công”.

## 13. Checklist ghi nhớ

- [ ] Vẽ được `Throwable` với hai nhánh tiêu biểu `Exception` và `Error`.
- [ ] Giải thích được `RuntimeException` là unchecked nhưng vẫn thuộc `Exception`.
- [ ] Giải thích được tại sao `AssertionError` không bị `catch (Exception)` bắt.
- [ ] Chạy `CatchExceptionBoundaryDemo.java` và giải thích bốn dòng stdout.
- [ ] Chạy `ThrowableTypeDemo.java` và xác nhận một lớp có thể `extends Throwable` trực tiếp.
- [ ] Phân biệt kiểm tra checked/unchecked lúc biên dịch với matching handler lúc chạy.
- [ ] Nêu được một trường hợp `catch (Throwable)` có thể hợp lý và vì sao nó cần chính sách rất chặt.
- [ ] Đọc được wrapper exception và `getCause()` mà không nhầm kiểu lỗi đang được ném.
- [ ] Hiểu rằng `finally`/try-with-resources hỗ trợ dọn dẹp nhưng không tự xử lý hay rollback lỗi.
- [ ] Luôn ưu tiên bắt kiểu lỗi mình có khả năng xử lý thay vì nuốt `Throwable`.

**Kết luận:** `catch (Exception)` là một handler rộng **bên trong nhánh `Exception`**, không phải handler của mọi `Throwable`. `Error` và các nhánh khác của `Throwable` nằm ngoài phạm vi đó. Sự khác biệt đến từ cây kế thừa và quy tắc matching của Java, còn quyết định có nên bắt lỗi hay không phụ thuộc khả năng phục hồi và hợp đồng xử lý ở từng tầng ứng dụng.

</details>

- [Quay lại đầu trang](#back-to-top)
