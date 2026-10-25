<a id="back-to-top"></a>

# Lệnh throw

## Menu
- [Cú pháp throw và yêu cầu kiểu Throwable](#throw-statement-001)
- [Tạo đối tượng ngoại lệ bằng constructor](#throw-statement-002)
- [Phân biệt tạo ngoại lệ với ném ngoại lệ](#throw-statement-003)
- [Ném ngoại lệ có thông điệp và ngữ cảnh phù hợp](#throw-statement-004)
- [throw null và NullPointerException](#throw-statement-005)
- [Ném lại cùng đối tượng ngoại lệ bằng throw e](#throw-statement-006)
- [Đường thực thi không thể tới sau throw](#throw-statement-007)
- [Throw trong nhánh switch và luồng điều khiển có trả về giá trị](#throw-statement-008)

## <a id="throw-statement-001">Cú pháp throw và yêu cầu kiểu Throwable</a>

<details>
<summary>Xem chi tiết</summary>

# Cú pháp `throw` và yêu cầu kiểu `Throwable` trong Java

> **Phạm vi:** Java Core → Exception → Lệnh `throw`. Câu lệnh `throw` dùng để **ném một đối tượng biểu diễn lỗi hoặc tình huống bất thường**, làm luồng thực thi rời khỏi đường đi bình thường. Trong Java, biểu thức sau `throw` phải có kiểu phù hợp với `java.lang.Throwable`; kiểm tra kiểu lúc biên dịch khác với việc xác định đối tượng nào thực sự được ném khi chạy.

## 1. Cú pháp chuẩn của `throw`

Dạng tổng quát:

```java
throw expression;
```

Trong đó:

- `throw` là từ khóa Java.
- `expression` phải là biểu thức có kiểu tham chiếu có thể gán cho `Throwable` (bao gồm trường hợp biểu thức `null`; xem Mục 7).
- Dấu chấm phẩy `;` kết thúc **câu lệnh**.
- Khi chạy, biểu thức được đánh giá, sau đó đối tượng `Throwable` tương ứng được ném. Nếu giá trị nhận được là `null`, JVM ném `NullPointerException` thay thế.
- `throw` là một **statement**, không phải một toán tử trả giá trị hoặc một expression thông thường.

Cách viết thường gặp nhất:

```java
throw new IllegalArgumentException("Số lượng phải lớn hơn 0");
```

`new IllegalArgumentException(...)` tạo đối tượng ngoại lệ; `throw` ném đối tượng đó. Chỉ khởi tạo ngoại lệ mà không ném thì không làm luồng thực thi chuyển sang đường xử lý lỗi:

```java
new IllegalArgumentException("Giá trị không hợp lệ"); // Chỉ tạo đối tượng.
```

**Kết luận đầu tiên:** `throw` không phải lời gọi hàm trả về một giá trị đặc biệt. Nó là câu lệnh điều khiển luồng thực thi bằng một `Throwable`.

### 1.1. Những hình thức hợp lệ

Các đoạn sau là **mảnh code minh họa bên trong một method**, chưa phải chương trình độc lập:

```java
// Tạo rồi ném một unchecked exception.
throw new IllegalArgumentException("Sai tham số");
```

```java
// Ném đối tượng exception đã có (phương thức phải khai báo/bắt nếu checked).
java.io.IOException cause = new java.io.IOException("Lỗi đọc");
throw cause;
```

```java
// Dùng biểu thức điều kiện tạo ra một trong hai RuntimeException.
throw invalidInput
        ? new IllegalArgumentException("Dữ liệu sai")
        : new IllegalStateException("Trạng thái sai");
```

Đoạn thứ ba giả định `invalidInput` đã được khai báo là `boolean`. Toàn bộ biểu thức điều kiện trả về một đối tượng phù hợp với `Throwable`. Tuy nhiên bản thân `throw` vẫn là **câu lệnh**; không thể đặt tùy ý như một nhánh trả giá trị của toán tử `?:`.

Ví dụ sai:

```java
// KHÔNG hợp lệ: throw là statement, không là expression trong toán tử ?:.
// int value = condition ? 10 : throw new IllegalArgumentException();
```

Cách diễn đạt đúng là dùng `if`/`throw` rồi trả giá trị hợp lệ ở nhánh còn lại.

### 1.2. `throw` không phải `throws`

| Tiêu chí | `throw` | `throws` |
| --- | --- | --- |
| Vị trí | Trong thân phương thức, constructor, khối lệnh phù hợp | Trong khai báo phương thức hoặc constructor |
| Vai trò | Thực sự ném một đối tượng `Throwable` | Khai báo các loại exception có thể thoát ra, đặc biệt là checked exceptions |
| Dạng | `throw new IOException("...");` | `void read() throws IOException { ... }` |
| Số đối tượng/loại | Mỗi câu lệnh ném một giá trị `Throwable` tại lần chạy đó | Có thể liệt kê nhiều kiểu sau dấu phẩy |
| Ảnh hưởng lên control flow | Làm đường thực thi bình thường tại đó dừng | Tự nó không ném đối tượng nào |
| Có thể dùng cùng nhau? | Có | Có; một method có thể `throw` exception checked và khai báo `throws` tương ứng |

Ví dụ method minh họa:

```java
static void requireData(String data) throws java.io.IOException {
    if (data == null) {
        throw new java.io.IOException("Thiếu dữ liệu");
    }
}
```

`throws IOException` thông báo cho caller nghĩa vụ xử lý/khai báo; `throw new IOException(...)` mới phát sinh ngoại lệ khi `data == null`. Lựa chọn `IOException` trong ví dụ nhằm diễn đạt lỗi dữ liệu từ nguồn I/O; để kiểm tra tham số đầu vào thuần túy, `IllegalArgumentException` hoặc `NullPointerException` có thể đúng ngữ nghĩa hơn.

## 2. Vì sao bắt buộc phải tương thích với `Throwable`?

Java định nghĩa cây kế thừa của các đối tượng có thể ném qua `throw`:

```text
java.lang.Object
└── java.lang.Throwable
    ├── java.lang.Exception
    │   ├── java.io.IOException
    │   └── java.lang.RuntimeException
    │       ├── IllegalArgumentException
    │       ├── NullPointerException
    │       └── IllegalStateException
    └── java.lang.Error
        ├── AssertionError
        ├── LinkageError
        └── VirtualMachineError
```

Đây là một phần tiêu biểu của cây, không phải danh sách đầy đủ.

- `Throwable` là lớp cha chung của `Exception` và `Error`.
- `Exception` là nhánh thường dùng cho các tình huống ngoại lệ của chương trình.
- `RuntimeException extends Exception`: những exception này là **unchecked**.
- `Error extends Throwable`: cũng là unchecked, nhưng thông thường không nên tạo/bắt để mô phỏng lỗi nghiệp vụ.
- Có thể tạo lớp `extends Throwable` trực tiếp; Java cho phép về cú pháp, nhưng thực tế thường chọn `Exception` hoặc `RuntimeException` cho API ứng dụng.

Việc kiểu phải là `Throwable` bảo đảm cơ chế `throw`/`catch` làm việc với đối tượng có đặc tính của Throwable, như thông điệp lỗi, nguyên nhân, stack trace, suppressed exceptions và phân cấp kiểu rõ ràng. Nó cũng giúp trình biên dịch kiểm tra hợp đồng checked exception.

### 2.1. Biểu thức `throw` có thể là những gì?

Một biểu thức hợp lệ có thể có dạng:

- `new SomeException(...)` — tạo và ném ngay.
- `throw e;` — ném biến tham chiếu đến một ngoại lệ.
- `throw createException();` — ném giá trị trả về từ phương thức có kiểu phù hợp.
- `throw condition ? exceptionA : exceptionB;` — ném kết quả của một biểu thức điều kiện có kiểu tương thích.
- `throw null;` — hợp lệ cú pháp nhưng khi chạy ném `NullPointerException`, không phải cách khuyến khích viết.

Kiểu được xem xét khi biên dịch là **kiểu tĩnh của biểu thức**; đối tượng cụ thể được ném khi chạy mang kiểu runtime thực tế, quyết định `catch` nào phù hợp.

## 3. Ví dụ Java 17 chạy được: kiểm tra đầu vào bằng `throw`

Tạo tệp `ThrowBasicDemo.java`:

```java
public class ThrowBasicDemo {
    static int requirePositive(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("Giá trị phải dương");
        }
        return value;
    }

    public static void main(String[] args) {
        for (int input : new int[] {5, 0, -2}) {
            try {
                int result = requirePositive(input);
                System.out.println("OK: " + result);
            } catch (IllegalArgumentException e) {
                System.out.println("Lỗi " + input + ": " + e.getMessage());
            }
        }
    }
}
```

Biên dịch/chạy với JDK 17 trở lên:

```bash
javac -encoding UTF-8 ThrowBasicDemo.java
java ThrowBasicDemo
```

Stdout chính xác:

```text
OK: 5
Lỗi 0: Giá trị phải dương
Lỗi -2: Giá trị phải dương
```

**Đường đi với từng input:**

1. `5`: điều kiện `value <= 0` sai, trả về `5` rồi in `OK: 5`.
2. `0`: tạo `IllegalArgumentException`, ném bằng `throw`; câu `return value` không chạy. `catch` ở `main` nhận đối tượng và in thông điệp.
3. `-2`: tương tự `0`; vòng lặp vẫn tiếp tục sau khi `catch` hoàn thành.

Một ngoại lệ **không tự động kết thúc toàn bộ JVM**. Nó làm luồng thực thi thay đổi; có thể được xử lý ở caller, truyền tiếp, hoặc nếu không được xử lý có thể làm thread kết thúc bất thường.

### 3.1. Vì sao dùng `IllegalArgumentException`?

Phương thức `requirePositive` có tiền điều kiện tham số phải dương. `IllegalArgumentException` diễn đạt rõ việc caller truyền dữ liệu vi phạm hợp đồng, là unchecked exception nên không cần khai báo `throws`. Nếu đầu vào sai là trạng thái thường xuyên dự kiến (chẳng hạn kiểm tra form), một API trả về kết quả validation có cấu trúc cũng có thể phù hợp. Chọn cách nào là quyết định thiết kế, không phải một yêu cầu của cú pháp `throw`.

## 4. Các kiểu không hợp lệ sau `throw`

Các ví dụ sau **cố ý sai để giải thích quy tắc**, không dùng trong tệp Java chạy:

```java
throw "Không hợp lệ";           // Sai: String không extends Throwable.
throw 123;                     // Sai: int là primitive.
throw new Object();            // Sai: Object không phải Throwable.
throw System.out.println("x"); // Sai: println() trả về void.
```

Không thể chỉ có tên `Error`/`Exception` trong text mà biến nó thành exception. Cần một **đối tượng thực tế thuộc kiểu Throwable** hoặc biểu thức hợp lệ có kiểu phù hợp.

Ví dụ muốn báo lỗi từ chuỗi, viết:

```java
throw new IllegalStateException("Không hợp lệ");
```

Java không có cơ chế chuyển mọi đối tượng `Object` thành một Throwable bằng ép kiểu. Thử `(Throwable) new Object()` có thể qua kiểm tra kiểu trong một số ngữ cảnh nếu cast reference được cho phép, nhưng tại runtime sẽ thất bại do đối tượng không phải Throwable; không nên dùng cast để né yêu cầu kiểu.

### 4.1. Kiểu tĩnh quan trọng ngay cả khi đối tượng runtime đúng

Đoạn minh họa dưới đây:

```java
Object problem = new IllegalArgumentException("Dữ liệu sai");
// throw problem; // Không biên dịch: kiểu tĩnh Object không gán được cho Throwable.
```

Cùng một đối tượng nhưng được giữ trong biến kiểu `Object`. Để ném, cần thu hẹp kiểu một cách hợp lệ, ví dụ:

```java
Object problem = new IllegalArgumentException("Dữ liệu sai");
if (problem instanceof Throwable failure) {
    throw failure; // Kiểu tĩnh Throwable: còn phải xét quy tắc checked exception.
}
```

Đoạn trên **không phải chương trình hoàn chỉnh**. `failure` có kiểu tĩnh `Throwable`, nên method chứa nó thông thường phải bắt hoặc khai báo `throws Throwable`, mặc dù đối tượng cụ thể lúc chạy là `IllegalArgumentException`. Không nên lạm dụng `throws Throwable` chỉ để phù hợp với một biến quá tổng quát; nếu biết chắc chỉ ném unchecked exception, nên biểu đạt kiểu `RuntimeException` ngay trong hợp đồng.

### 4.2. `Throwable` là kiểu tham chiếu, không phải một chuỗi/mã lỗi

`throw` không nhận mã lỗi `404`, text `"not found"` hay giá trị `false` như một vài ngôn ngữ khác. Có thể đặt những dữ liệu đó trong exception (message, field, mã trạng thái) nhưng đối tượng được ném vẫn phải là `Throwable`.

## 5. Checked và unchecked ảnh hưởng đến `throw` thế nào?

Hãy tách hai bước:

1. **Kiểm tra kiểu:** biểu thức sau `throw` phải có kiểu phù hợp với `Throwable`.
2. **Kiểm tra checked exception:** nếu exception có thể thoát ra là checked, code phải bắt hoặc khai báo phù hợp.

Hai điều kiện độc lập. Một biểu thức có kiểu `Throwable` hợp lệ vẫn có thể bị compiler từ chối nếu checked exception chưa được xử lý.

| Kiểu được ném | Nằm trong cây Throwable? | Checked/unchecked | Có cần `catch` hoặc `throws` khi thoát khỏi method? |
| --- | --- | --- | --- |
| `IOException` | Có | Checked | Có |
| `Exception` | Có | Checked | Có |
| `Throwable` | Có | Checked | Có |
| `IllegalArgumentException` | Có | Unchecked (`RuntimeException`) | Không bắt buộc |
| `NullPointerException` | Có | Unchecked (`RuntimeException`) | Không bắt buộc |
| `AssertionError` | Có | Unchecked (`Error`) | Không bắt buộc |
| `String` | Không | Không áp dụng | Không thể `throw` trực tiếp |

**Quy tắc:** các lớp con của `RuntimeException` và `Error` là unchecked. Những lớp con khác của `Throwable` là checked theo hệ thống ngôn ngữ Java. Vì thế không thể đồng nhất `Exception` với toàn bộ checked exception hay `Throwable` với một kiểu unchecked.

### 5.1. Ví dụ sai do chưa xử lý checked exception

Trong một method **không khai báo `throws`** và không có handler phù hợp:

```java
static void fail() {
    throw new java.io.IOException("Không đọc được");
}
```

Đoạn trên không biên dịch vì `IOException` là checked exception. Thông báo compiler thường có dạng “unreported exception IOException; must be caught or declared to be thrown”.

Hai cách xử lý hợp lệ:

**Cách A — khai báo cho caller:**

```java
static void fail() throws java.io.IOException {
    throw new java.io.IOException("Không đọc được");
}
```

**Cách B — bắt tại nơi có thể xử lý:**

```java
static void fail() {
    try {
        throw new java.io.IOException("Không đọc được");
    } catch (java.io.IOException e) {
        System.out.println("Đã xử lý: " + e.getMessage());
    }
}
```

Hai đoạn là ví dụ trong thân lớp, không phải các tệp độc lập. Khi xử lý thực tế, đừng bắt checked exception rồi im lặng nếu không có hướng phục hồi; caller có thể tưởng thao tác đã thành công.

### 5.2. `throws` không phải sự bảo đảm “chỉ có exception này”

`static void process() throws IOException` cho biết method có thể để `IOException` thoát ra. Nó không cấm một `IllegalArgumentException`, `NullPointerException` hoặc `Error` xuất hiện khi chạy. `throws` cũng không bảo đảm method chắc chắn sẽ ném exception.

Tương tự, viết `throw new RuntimeException("...")` không cần `throws RuntimeException`; việc khai báo unchecked exception là tùy chọn và thường dùng Javadoc `@throws` để diễn đạt hợp đồng rõ hơn.

## 6. Chương trình Java 17: ném checked exception rồi xử lý ở caller

Ví dụ dùng `BufferedReader` trên dữ liệu nằm trong RAM để tránh phụ thuộc file và vẫn minh họa API đọc có khả năng ném `IOException`. Khi không có dòng nào, method chủ động ném `EOFException` (một lớp con checked của `IOException`).

Tạo tệp `ThrowCheckedDemo.java`:

```java
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.StringReader;

public class ThrowCheckedDemo {
    static String requireFirstLine(String content) throws IOException {
        try (BufferedReader reader =
                     new BufferedReader(new StringReader(content))) {
            String line = reader.readLine();
            if (line == null) {
                throw new EOFException("Không có dòng dữ liệu");
            }
            return line;
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println("Dòng đầu: "
                    + requireFirstLine("Java\nException"));
            System.out.println("Dòng đầu: " + requireFirstLine(""));
        } catch (EOFException e) {
            System.out.println("Hết dữ liệu: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Lỗi I/O: " + e.getMessage());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 ThrowCheckedDemo.java
java ThrowCheckedDemo
```

Stdout chính xác:

```text
Dòng đầu: Java
Hết dữ liệu: Không có dòng dữ liệu
```

Giải thích:

- `readLine()` trả `"Java"` khi có dòng đầu; method `return` bình thường.
- Với chuỗi rỗng, `readLine()` trả `null`; `throw new EOFException(...)` được chạy. `EOFException` là checked exception nên phải được bắt hoặc khai báo khi thoát khỏi method.
- `requireFirstLine` khai báo `throws IOException`, bao gồm `EOFException` vì nó là lớp con của `IOException`.
- `main` có `catch (EOFException e)` trước `catch (IOException e)`. Nếu đảo thứ tự, handler cho `EOFException` không thể tới được và chương trình không biên dịch.
- Khi ném `EOFException`, câu `return line` bị bỏ qua. Try-with-resources đóng `reader` khi rời khối.
- Dữ liệu ở ví dụ là in-memory; trường hợp file thật có thể phát sinh thêm lỗi môi trường ngoài dự kiến.

Chú ý: trong thực tế, EOF là kết quả bình thường của nhiều API đọc, không luôn cần ném exception. Ở đây ta **chủ động xây hợp đồng** “phải có ít nhất một dòng”, nên không có dòng được coi là thất bại cần báo.

## 7. Các trường hợp mở rộng — nhận diện quy tắc cần thiết

Những trường hợp dưới đây giúp người học áp dụng **cú pháp `throw`** mà không lặp lại toàn bộ chương trình minh họa của những bài chuyên sâu riêng về `throw null`, rethrow, unreachable code và switch.

### 7.1. Ném tham chiếu null

```java
throw null; // Biên dịch hợp lệ, runtime ném NullPointerException.
```

Giá trị null không phải đối tượng `Throwable`. Nếu biểu thức được đánh giá thành null, JVM ném một `NullPointerException`. Biến có kiểu `RuntimeException` vẫn có thể chứa null; code nghiệp vụ nên tạo exception có message rõ thay vì cố ý dùng `throw null`.

### 7.2. Ném lại hoặc bọc lỗi

```java
try {
    loadSettings();
} catch (java.io.IOException e) {
    throw new IllegalStateException("Không tải được cấu hình", e);
}
```

Đây là snippet minh họa, `loadSettings` được định nghĩa trong ứng dụng. `throw e;` ném lại **cùng đối tượng** đã bắt; `throw new ...(..., e)` tạo exception mới với `e` làm cause. Ném lại checked exception tuân thủ `catch-or-declare`, với quy tắc `precise rethrow` trong một số `catch` effectively final.

### 7.3. Sau `throw` và trong `switch`

```java
if (quantity <= 0) {
    throw new IllegalArgumentException("quantity must be positive");
}
System.out.println("valid");
```

Đây là đoạn minh họa với `quantity` đã khai báo. `throw` không hoàn tất bình thường: lệnh ngay sau một `throw` vô điều kiện **trong cùng khối** là unreachable, nhưng đường ngoài nhánh `if` vẫn có thể chạy. Trong Java 17, `switch expression` cũng cho phép một nhánh `default -> throw new IllegalArgumentException(...)` bên cạnh các nhánh cung cấp giá trị hoặc `yield`; `throw` vẫn là statement, không phải một expression trả giá trị.

### 7.4. Bối cảnh rộng hơn

`throw` có thể nằm trong constructor, thân khối lambda, vòng lặp hoặc khai báo một lỗi tùy biến theo cú pháp phù hợp; ngoại lệ được ném vẫn phải thuộc nhánh `Throwable`, và checked exception còn chịu giới hạn chữ ký method/functional interface. Thường định nghĩa exception nghiệp vụ bằng cách kế thừa `Exception` hoặc `RuntimeException`, không lạm dụng `Error`. `finally`/`try-with-resources` thực hiện cleanup theo contract nhưng **không tự rollback** dữ liệu đã thay đổi.

Phần này là bản đồ cú pháp. Các bài riêng trong chương phân tích identity, stack trace, null semantics, reachability theo JLS và `yield` trong `switch` qua ví dụ chuyên sâu, tránh lặp các walkthrough đó tại đây.

## 8. Lỗi thường gặp khi dùng `throw`

| Lỗi hoặc hiểu nhầm | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| `throw "Lỗi";` | `String` không phải `Throwable`. | `throw new IllegalArgumentException("Lỗi");` hoặc loại phù hợp. |
| `throw new Object();` | `Object` không tương thích `Throwable`. | Dùng exception thuộc hệ `Throwable`. |
| `throw new IOException(...);` nhưng không xử lý checked | Compiler yêu cầu bắt hoặc khai báo. | Thêm `throws IOException` hoặc `catch` đúng tầng. |
| Tưởng `throws IOException` tự phát sinh lỗi | `throws` chỉ khai báo, không thực thi. | Dùng `throw` khi điều kiện thất bại xảy ra. |
| Tạo `new Exception(...)` nhưng không `throw` | Chỉ tạo object. | Viết `throw new Exception(...)` và xử lý checked. |
| `throw null;` để báo thiếu dữ liệu | Runtime ném `NullPointerException` thay thế, khó hiểu. | Chủ động tạo exception rõ nghĩa. |
| `Throwable x = new RuntimeException(...); throw x;` rồi bất ngờ phải khai báo | Kiểu tĩnh `Throwable` được kiểm tra như checked. | Dùng kiểu hẹp hợp lý; hiểu precise rethrow khi bắt. |
| Bắt lỗi rồi `throw new RuntimeException(e.getMessage())` | Mất cause/chuỗi lỗi. | Dùng constructor nhận `cause` khi bọc lỗi. |
| Đặt code sau `throw` vô điều kiện | Code không thể tới được. | Dùng `if`, sắp xếp control flow rõ ràng. |
| Tưởng `throw` sẽ rollback | Java không tự hoàn tác mọi side effect. | Xây hợp đồng trạng thái, validate trước hoặc transaction thích hợp. |
| `catch (Exception)` để bắt `Error` tự ném | `Error` không kế thừa `Exception`. | Hiểu cây `Throwable`; không tùy tiện bắt rộng. |
| Sử dụng exception để điều khiển mọi nhánh bình thường | Code khó đọc và khó phân biệt lỗi thực. | Dùng điều kiện/kết quả thông thường cho tình huống có thể biểu diễn đơn giản. |

### 8.1. Lỗi về tên tệp, import và output

- `public class ThrowBasicDemo` phải nằm trong `ThrowBasicDemo.java`. Tên tệp Java phân biệt chữ hoa/chữ thường trên các hệ thống tương ứng.
- `java.lang.RuntimeException`, `IllegalArgumentException`, `Throwable` được import tự động từ `java.lang`; `IOException` và `EOFException` thuộc `java.io` nên ví dụ có `import`.
- Stack trace của một exception không được bắt thường được in ra **stderr**, có line number phụ thuộc phiên bản tệp. Các stdout được ghi trong bài đều xuất phát từ chương trình đã chủ động bắt lỗi và in thông tin ổn định.
- Mã được đánh dấu “minh họa” có thể dựa vào biến, phương thức hoặc lớp bao quanh chưa được cung cấp. Chỉ những chương trình có tên tệp, `public class` và `main` ở các Mục 3 và 6 là các ví dụ biên dịch/chạy độc lập.

## 9. Bài tập có đáp án định hướng

### Bài 1 — Chọn câu lệnh `throw` hợp lệ

Đánh giá:

```java
throw new RuntimeException("A");
throw new java.io.IOException("B");
throw new Throwable("C");
throw null;
throw "D";
```

Các dòng được hiểu **độc lập**, không viết nối nhau trong cùng một block như chương trình hoàn chỉnh.

- `new RuntimeException`: kiểu hợp lệ, unchecked, không cần `throws`.
- `new IOException`: kiểu hợp lệ nhưng checked; cần bắt/khai báo nếu thoát ra.
- `new Throwable`: kiểu hợp lệ nhưng checked; cần bắt/khai báo nếu thoát ra.
- `throw null`: cú pháp hợp lệ; khi chạy ném `NullPointerException`.
- `throw "D"`: không hợp lệ vì kiểu `String` không tương thích với `Throwable`.

### Bài 2 — Dự đoán đầu ra

Với `ThrowBasicDemo`, thêm `7` vào cuối mảng. Kết quả sẽ thêm dòng `OK: 7` sau các dòng đã có. Nếu thay `catch (IllegalArgumentException)` bằng `catch (Exception)`, các lỗi được ném trong ví dụ vẫn bị bắt, nhưng kiểu handler rộng hơn và dễ che các loại lỗi không mong muốn.

### Bài 3 — Biến kiểu rộng

Cho:

```java
Object value = new IllegalStateException("Sai");
```

Tại sao `throw value;` không biên dịch? Bởi kiểu tĩnh của `value` là `Object`, không thỏa ràng buộc kiểu của `throw`. Nếu sử dụng pattern matching `instanceof Throwable error` rồi `throw error`, tại sao còn nghĩa vụ checked? Vì biến `error` có kiểu tĩnh `Throwable`.

### Bài 4 — Khác biệt giữa `throw e` và bọc cause

Trong `ThrowCauseDemo`, thử thay:

```java
throw new ConfigurationException("Cổng không hợp lệ", e);
```

bằng `throw e;`. Method `loadPort` vẫn có thể biên dịch vì `NumberFormatException` là unchecked, nhưng hợp đồng/thông tin mà `main` chờ sẽ thay đổi: handler `catch (ConfigurationException)` không còn bắt được exception gốc. Thiết kế loại wrapper cần nhất quán với hợp đồng của caller.

### Bài 5 — Kiểm thử đường lỗi

Viết một method `parseAge(String input)`:

1. Nếu chuỗi không phải số nguyên, cho `NumberFormatException` lan lên.
2. Nếu parse được nhưng giá trị ngoài khoảng hợp lệ do bạn xác định, `throw new IllegalArgumentException(...)`.
3. Nếu hợp lệ, trả về `int`.
4. Từ `main`, thử các chuỗi hợp lệ, chữ, âm và vượt phạm vi `int`.
5. Kiểm tra rằng phương thức không sửa trạng thái dùng chung trước khi ném exception, và ghi rõ hợp đồng của từng kiểu lỗi.

## 10. Checklist trước khi sử dụng `throw` trong API

- [ ] Biểu thức sau `throw` có kiểu tĩnh phù hợp với `Throwable` không?
- [ ] Có hiểu rằng giá trị `null` ở đây sinh `NullPointerException` khi chạy không?
- [ ] Đã phân biệt `throw` thực thi với `throws` khai báo chưa?
- [ ] Đã xác định ngoại lệ là checked hay unchecked và nghĩa vụ của caller chưa?
- [ ] Đã chọn exception diễn đạt đúng ngữ nghĩa, thay vì lạm dụng `Error` chưa?
- [ ] Nếu ném lại exception, có giữ nguyên thông tin lỗi cần thiết không?
- [ ] Nếu bọc exception, có truyền nguyên nhân gốc qua `cause` không?
- [ ] Code phía sau `throw` có đường chạy hợp lệ hay đã vô tình thành unreachable?
- [ ] Các tài nguyên cần đóng và trạng thái đã thay đổi trước `throw` có được xử lý đúng hợp đồng không?
- [ ] Có test cả đường thành công và đường ném lỗi, bao gồm loại exception và trạng thái sau lỗi không?
- [ ] Đã xác định vị trí `catch` có thể xử lý phù hợp chưa?

**Ghi nhớ:** `throw expression;` ném một giá trị phù hợp với `Throwable` và kết thúc bất thường đường chạy hiện tại. Chữ ký `throws` và cơ chế checked exception quyết định nghĩa vụ compile-time; phân cấp runtime quyết định handler `catch`; còn dữ liệu đã thay đổi và tài nguyên cần dọn phải được thiết kế riêng — chúng không tự hoàn tác chỉ vì có `throw`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="throw-statement-002">Tạo đối tượng ngoại lệ bằng constructor</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo đối tượng ngoại lệ bằng constructor trong Java

Trong Java, ngoại lệ là **đối tượng**. Trước khi một ngoại lệ có thể được ném bằng lệnh `throw`, chương trình cần có một tham chiếu đến đối tượng thuộc lớp kế thừa `Throwable`. Cách thường gặp nhất là gọi **constructor** với toán tử `new`:

~~~java
throw new IllegalArgumentException("Tuổi phải lớn hơn hoặc bằng 0");
~~~

Câu lệnh này gồm **hai hành động khác nhau**:

1. `new IllegalArgumentException("...")` tạo một đối tượng ngoại lệ, truyền thông điệp vào constructor.
2. `throw` ném tham chiếu đến đối tượng đó, làm luồng thực thi bình thường bị ngắt và bắt đầu quá trình tìm `catch` phù hợp.

Có thể tạo đối tượng trước rồi ném sau; riêng việc tạo đối tượng **chưa làm phát sinh luồng điều khiển ngoại lệ**. Bài này giải thích constructor của các lớp ngoại lệ, cách truyền message và cause, cách tự định nghĩa ngoại lệ và những lỗi dễ mắc khi sử dụng `throw new`.

## 1. Ngoại lệ là đối tượng kế thừa `Throwable`

Phân cấp rút gọn:

~~~text
java.lang.Object
└── java.lang.Throwable
    ├── java.lang.Error
    └── java.lang.Exception
        ├── java.io.IOException
        └── java.lang.RuntimeException
            ├── java.lang.IllegalArgumentException
            │   └── java.lang.NumberFormatException
            ├── java.lang.IllegalStateException
            └── java.lang.NullPointerException
~~~

Các lớp này sở hữu hoặc kế thừa trạng thái và hành vi như:

- `getMessage()`: lấy thông điệp mô tả lỗi.
- `getCause()`: lấy ngoại lệ/nguyên nhân gốc được liên kết, nếu có.
- `getStackTrace()` và `printStackTrace()`: phục vụ chẩn đoán vị trí tạo/ghi nhận stack trace.
- `getSuppressed()`: lấy các ngoại lệ bị kèm theo, chẳng hạn khi đóng tài nguyên trong try-with-resources.

Constructor là nơi chương trình **khởi tạo** đối tượng này. Constructor không quyết định ngoại lệ có checked hay unchecked: điều đó phụ thuộc **kiểu lớp** của đối tượng. Ví dụ:

- `new IOException("...")` tạo checked exception.
- `new IllegalArgumentException("...")` tạo unchecked exception.
- Cả hai đều có thể được lưu vào biến, truyền cho phương thức hoặc ném bằng `throw`.

Với một lớp `Throwable`, `new` không tự động đồng nghĩa với `throw`.

## 2. Phân biệt khởi tạo ngoại lệ với ném ngoại lệ

`new IllegalArgumentException("x")` tạo đối tượng exception và **không tự truyền lỗi tới caller**; `throw new IllegalArgumentException("x");` vừa tạo vừa ném. Nếu đã có biến `e` phù hợp với `Throwable`, `throw e;` ném đối tượng đang tham chiếu mà không gọi constructor mới. `throws IOException` ở chữ ký method chỉ mô tả khả năng checked exception thoát ra, không tạo hay ném đối tượng.

```java
IllegalArgumentException ex = new IllegalArgumentException("Sai");
System.out.println(ex.getMessage()); // Vẫn chạy bình thường.
throw ex; // Bắt đầu chuyển điều khiển theo exception.
```

Đây là snippet trong một method. Tạo `new IOException(...)` cũng không tự phát sinh nghĩa vụ `catch-or-declare` nếu không có đường thực sự ném checked exception. Trọng tâm của bài này là **constructor, message, cause và super(...)**; diễn biến chi tiết của `new` so với `throw` được trình bày trong bài phân biệt hai thao tác.

## 3. Những dạng constructor phổ biến

Các lớp ngoại lệ chuẩn có thể cung cấp một hay nhiều constructor trong số các dạng sau. **Không phải mọi lớp ngoại lệ đều có đầy đủ tất cả dạng này**; phải kiểm tra API của kiểu cụ thể.

### Constructor không tham số

~~~java
RuntimeException issue = new RuntimeException();
~~~

Ở ví dụ này, `issue.getMessage()` là `null` vì không có thông điệp được truyền vào. Một số lớp cũng cung cấp constructor không tham số, nhưng không nên mặc định mọi lớp đều có.

### Constructor nhận thông điệp: `(String message)`

~~~java
IllegalArgumentException issue =
        new IllegalArgumentException("age phải >= 0");
~~~

Thông điệp thường được lưu lại để trả về qua `getMessage()`. Message nên nói rõ **điều kiện nào không đạt**, đủ để người đọc log hiểu ngữ cảnh:

- Hữu ích: `"age phải nằm trong khoảng 0..150"`.
- Khó chẩn đoán: `"Lỗi"` hoặc `"Có vấn đề"`.

Message là dữ liệu chẩn đoán, không phải lựa chọn tốt để điều khiển luồng bằng cách so sánh chuỗi; văn bản có thể thay đổi hoặc được bản địa hóa.

### Constructor nhận nguyên nhân: `(Throwable cause)`

~~~java
IllegalStateException issue =
        new IllegalStateException(
                new java.io.IOException("Không đọc được cấu hình"));
~~~

Ví dụ dùng constructor `IllegalStateException(Throwable)` có sẵn. `issue.getCause()` trả về đối tượng `IOException` đã truyền vào. Với constructor dạng này được kế thừa theo thiết kế của `Throwable`, thông điệp của ngoại lệ mới thường được tạo từ `cause.toString()` nếu nguyên nhân khác `null`. Thông điệp đó không nhất thiết mô tả đủ ngữ cảnh ở tầng cao hơn.

### Constructor nhận cả thông điệp và nguyên nhân: `(String message, Throwable cause)`

~~~java
java.io.IOException lowLevel =
        new java.io.IOException("Tệp không tồn tại");

IllegalStateException issue =
        new IllegalStateException(
                "Không thể khởi tạo ứng dụng", lowLevel);
~~~

Khi cần chuyển đổi loại ngoại lệ giữa các tầng, constructor dạng này thường hữu ích nhất:

- `issue.getMessage()` trả về `"Không thể khởi tạo ứng dụng"`.
- `issue.getCause() == lowLevel` là `true`.
- Ngoại lệ tầng cao giải thích **thao tác nào thất bại**; cause giữ nguyên thông tin gốc để điều tra.

Nếu chỉ gọi `new IllegalStateException("Không thể khởi tạo ứng dụng")` khi đang bọc lỗi thấp tầng, liên kết cause sẽ mất, trừ khi được thiết lập riêng bằng cách hợp lệ.

### Constructor nào thực sự có sẵn?

Các ví dụ trên dùng constructor tồn tại ở `RuntimeException`, `IllegalStateException`, `IOException` và `IllegalArgumentException` trong Java 17+. Một lớp cụ thể có thể chỉ hỗ trợ vài dạng hoặc yêu cầu tham số đặc biệt. Chẳng hạn:

~~~java
NumberFormatException bad =
        new NumberFormatException("Không phải số nguyên");
~~~

Constructor `NumberFormatException(String)` hợp lệ; tuy nhiên không thể mặc định lớp này có constructor `NumberFormatException(String, Throwable)` chỉ vì một số lớp cha có constructor tương tự. **Constructor không được kế thừa** như phương thức thông thường. Hãy tra chữ ký constructor của chính lớp cần tạo.

## 4. Chọn message và cause để bảo toàn thông tin lỗi

Giả sử ứng dụng chuyển một chuỗi cấu hình sang số nguyên:

~~~java
static int parsePort(String raw) {
    try {
        return Integer.parseInt(raw);
    } catch (NumberFormatException cause) {
        throw new IllegalArgumentException(
                "Cổng không phải số nguyên: " + raw, cause);
    }
}
~~~

Đây là một **phương thức minh họa**, không phải chương trình độc lập. Khi đầu vào không thể phân tích, `Integer.parseInt` ném `NumberFormatException`. Khối `catch` tạo một đối tượng `IllegalArgumentException` mới với:

- Message cho biết đầu vào nào không hợp lệ trong ngữ cảnh cổng mạng.
- Cause là `NumberFormatException` gốc.

Vì `NumberFormatException` cũng là lớp con của `IllegalArgumentException`, việc bọc ngoại lệ ở đây chủ yếu nhằm **bổ sung ngữ cảnh**, không thay đổi nhánh checked/unchecked. Cần cân nhắc liệu tầng gọi có cần biết lỗi đã được chuyển đổi không; không bắt rồi bọc mọi ngoại lệ nếu không có lý do.

### Không dùng message để giả lập cause

Ví dụ ít hữu ích:

~~~java
throw new IllegalStateException(
        "Không đọc được cấu hình: " + original.getMessage());
~~~

Nếu `original` là một biến ngoại lệ có thật trong phạm vi, đoạn này chỉ sao chép một phần văn bản; `getCause()` của ngoại lệ mới vẫn là `null`. Thay vào đó:

~~~java
throw new IllegalStateException(
        "Không đọc được cấu hình", original);
~~~

Đây là hai dòng **minh họa trong ngữ cảnh có biến `original`**, chưa phải chương trình hoàn chỉnh. Constructor nhận cause giữ liên kết có cấu trúc đến ngoại lệ gốc.

### Cẩn thận với dữ liệu nhạy cảm

Message có thể xuất hiện trong log, stack trace hoặc phản hồi lỗi nếu ứng dụng cấu hình thiếu chặt chẽ. Tránh đưa mật khẩu, token, thông tin xác thực hoặc toàn bộ nội dung tài liệu riêng tư vào chuỗi lỗi. Chỉ ghi những thông tin cần thiết và an toàn, ví dụ tên tham số, mã thao tác hoặc định danh đã được làm sạch.

## 5. Chương trình Java 17+ chạy độc lập

Ví dụ dưới đây minh họa bốn điều quan trọng:

1. Constructor tạo đối tượng trước khi ném.
2. `throw new` dùng constructor ngay tại điểm thất bại.
3. Ngoại lệ riêng nhận cả message và cause.
4. `getMessage()` và `getCause()` cho kết quả chính xác và có thể kiểm tra.

Tạo tệp **`ExceptionConstructorDemo.java`**:

~~~java
public class ExceptionConstructorDemo {

    static class ConfigurationException extends Exception {
        ConfigurationException(String message) {
            super(message);
        }

        ConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static void main(String[] args) {
        IllegalArgumentException prepared =
                new IllegalArgumentException("Tên không được trống");

        System.out.println("Tạo trước: " + prepared.getMessage());

        try {
            throw prepared;
        } catch (IllegalArgumentException e) {
            System.out.println("Bắt sau: " + e.getMessage());
            System.out.println("Cùng đối tượng: " + (e == prepared));
        }

        try {
            System.out.println("Cổng: " + readPort("abc"));
        } catch (ConfigurationException e) {
            System.out.println("Lỗi cấu hình: " + e.getMessage());
            System.out.println(
                    "Cause: " + e.getCause().getClass().getSimpleName());
        }

        try {
            System.out.println("Cổng: " + readPort("70000"));
        } catch (ConfigurationException e) {
            System.out.println("Lỗi cấu hình: " + e.getMessage());
            System.out.println("Cause: " + e.getCause());
        }

        System.out.println("Cổng: " + readPortOrDefault("8080", 80));
        System.out.println("Cổng mặc định: " + readPortOrDefault("abc", 80));
    }

    static int readPort(String raw) throws ConfigurationException {
        final int port;
        try {
            port = Integer.parseInt(raw);
        } catch (NumberFormatException cause) {
            throw new ConfigurationException(
                    "Cổng không phải số nguyên: " + raw, cause);
        }

        if (port < 1 || port > 65535) {
            throw new ConfigurationException(
                    "Cổng phải nằm trong khoảng 1..65535");
        }

        return port;
    }

    static int readPortOrDefault(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ExceptionConstructorDemo.java
java ExceptionConstructorDemo
~~~

Kết quả stdout:

~~~text
Tạo trước: Tên không được trống
Bắt sau: Tên không được trống
Cùng đối tượng: true
Lỗi cấu hình: Cổng không phải số nguyên: abc
Cause: NumberFormatException
Lỗi cấu hình: Cổng phải nằm trong khoảng 1..65535
Cause: null
Cổng: 8080
Cổng mặc định: 80
~~~

**Diễn giải:**

- `prepared` được tạo từ `new IllegalArgumentException(...)` rồi mới ném bằng `throw prepared;`. So sánh `e == prepared` cho `true` vì khối `catch` nhận **cùng tham chiếu** đối tượng đã ném; `throw` không tự tạo bản sao.
- `readPort("abc")` gặp lỗi chuyển đổi chuỗi. Constructor `ConfigurationException(String, Throwable)` gọi `super(message, cause)` nên giữ nguyên `NumberFormatException`; vì thế tên lớp cause được in là `NumberFormatException`.
- `readPort("70000")` chuyển được sang số nhưng vi phạm miền giá trị. Ngoại lệ được tạo bởi constructor chỉ nhận message; `getCause()` là `null`.
- `readPortOrDefault` minh họa lựa chọn xử lý tại tầng có giá trị dự phòng: `Integer.parseInt("abc")` ném ngoại lệ unchecked và phương thức chọn trả `80`. Hàm này chỉ kiểm tra khả năng phân tích số, **không kiểm tra miền cổng 1..65535**; với API thật, phải xác thực thêm nếu đó là yêu cầu hợp đồng.
- `ConfigurationException` là checked exception vì kế thừa `Exception` và không thuộc nhánh `RuntimeException`; các lời gọi `readPort` được đặt trong `try/catch` tương ứng.

Việc chủ động `new` và `throw` làm rõ ranh giới: **constructor xác định dữ liệu của ngoại lệ; `throw` chuyển điều khiển sang cơ chế xử lý ngoại lệ**.

## 6. Constructor của ngoại lệ tùy biến

Khi ngoại lệ chuẩn không biểu đạt đủ ý nghĩa của một lỗi trong miền ứng dụng, có thể khai báo lớp ngoại lệ riêng. Hai lựa chọn thường gặp:

### Ngoại lệ tùy biến loại checked

~~~java
public class ImportException extends Exception {
    public ImportException(String message) {
        super(message);
    }

    public ImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
~~~

Đây là **nội dung một lớp độc lập** nếu đặt trong `ImportException.java`. Kiểu này là checked, nên phương thức ném nó phải bắt hoặc khai báo theo quy tắc Java:

~~~java
static void importData() throws ImportException {
    throw new ImportException("Dữ liệu nhập thiếu trường bắt buộc");
}
~~~

Phương thức minh họa này cần nằm trong một lớp Java. Nếu thông tin nhập liệu có thể được người gọi xử lý một cách có cấu trúc, loại checked có thể giúp hợp đồng API yêu cầu người gọi cân nhắc thất bại.

### Ngoại lệ tùy biến loại unchecked

~~~java
public class InvalidOrderStateException extends RuntimeException {
    public InvalidOrderStateException(String message) {
        super(message);
    }

    public InvalidOrderStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
~~~

Đây là **nội dung một lớp độc lập** nếu lưu trong `InvalidOrderStateException.java`. Ngoại lệ này không bắt buộc được khai báo `throws` hay bắt ở bên gọi vì kế thừa `RuntimeException`.

Không có quy tắc “ngoại lệ riêng luôn phải checked” hoặc “mọi lỗi nghiệp vụ đều unchecked”. Chọn loại theo hợp đồng, khả năng xử lý của người gọi và quy ước thiết kế API.

### Vì sao constructor của lớp con gọi `super(...)`?

Constructor của lớp con **không tự động kế thừa constructor** của lớp cha. Khi viết `super(message)` hoặc `super(message, cause)`, bạn chuyển dữ liệu tới cơ chế khởi tạo của `Throwable` thông qua lớp cha tương ứng.

Nếu một lớp ngoại lệ tự định nghĩa constructor chỉ viết:

~~~java
public MyException(String message) {
    // Không gọi super(message).
}
~~~

thì nếu lớp có constructor cha không tham số phù hợp và mã biên dịch được, Java sẽ gọi `super()` ngầm định. Chuỗi `message` **không tự động được gán** vào trạng thái message của `Throwable`, nên `getMessage()` thường trả `null`. Để sử dụng được constructor như ví dụ, `MyException` phải được khai báo hợp lệ với lớp cha có constructor tương ứng; đoạn này chỉ minh họa khác biệt của việc gọi `super`.

Không nên tạo một trường `message` trùng nghĩa chỉ để “sửa” vấn đề trên. Hãy ưu tiên `super(message)` để giữ hành vi nhất quán với các API xử lý ngoại lệ trong Java.

## 7. Cause, `initCause` và chuỗi nguyên nhân

Các lớp ngoại lệ được xây dựng trên `Throwable` có thể giữ **chuỗi nguyên nhân**:

~~~text
IllegalStateException: Không thể khởi tạo ứng dụng
└── caused by IOException: Không đọc được cấu hình
    └── caused by FileNotFoundException: Thiếu file cấu hình
~~~

Hình trên mô tả ý tưởng của liên kết `getCause()`, không phải nguyên văn một stack trace do JVM in ra.

Thông thường, hãy ưu tiên constructor nhận cause:

~~~java
throw new IllegalStateException(
        "Không thể khởi tạo ứng dụng", original);
~~~

Nếu một lớp không có constructor nhận cause nhưng cho phép thiết lập cause sau khi tạo, `Throwable.initCause(Throwable)` có thể gán cause **một lần**:

~~~java
Exception wrapper = new Exception("Không thể xử lý");
wrapper.initCause(new java.io.IOException("Kết nối bị ngắt"));
~~~

Hai câu lệnh trên hợp lệ khi đặt trong phương thức Java. Tuy nhiên:

- `initCause` không thể gọi lại nhiều lần thành công trên cùng một đối tượng; gọi lại sẽ ném `IllegalStateException`.
- Không được đặt một ngoại lệ làm cause của chính nó.
- Nếu constructor đã **khởi tạo cause** (kể cả truyền `null` vào constructor nhận cause), `initCause` không thể thay thế lại.
- Khi lớp có constructor nhận `Throwable` hoặc `(String, Throwable)`, hãy dùng nó trực tiếp thay cho `initCause`.

**Cause** và **suppressed exceptions** là hai cơ chế khác nhau. Cause mô tả quan hệ nguyên nhân gây ra ngoại lệ bên ngoài; suppressed exceptions có thể giữ thêm các lỗi xảy ra trong cùng quá trình dọn dẹp, đặc biệt với try-with-resources. Không dùng `addSuppressed` làm bản thay thế tùy tiện cho cause.

## 8. Stack trace được tạo khi nào?

Mặc định, các constructor của `Throwable` thường ghi nhận stack trace tại thời điểm đối tượng được **khởi tạo**, thông qua cơ chế điền stack trace của lớp cha. Vì vậy:

~~~java
RuntimeException ex = new RuntimeException("Ví dụ");
// Có thể kiểm tra ex.getStackTrace() ở đây, dù chưa throw.
~~~

Đây là đoạn minh họa; stack trace không đồng nghĩa ngoại lệ đã bị ném hoặc được ghi log. Việc gọi `new RuntimeException(...)` thường đã tạo thông tin phục vụ chẩn đoán.

Một số tình huống quan trọng:

- Nếu tạo ngoại lệ ở một nơi rồi ném ở nơi khác, stack trace của chính đối tượng thường phản ánh **vị trí tạo** ngoại lệ.
- Ném lại **cùng đối tượng** bằng `throw e;` không tự động tạo một stack trace hoàn toàn mới tại điểm ném lại.
- Có thể tùy chỉnh cách ghi stack trace, ví dụ các constructor `Throwable`/`Exception`/`RuntimeException` có tùy chọn `writableStackTrace` dành cho các trường hợp đặc biệt; không nên tắt stack trace nếu cần điều tra lỗi.
- Tránh tái sử dụng cùng một đối tượng ngoại lệ cho nhiều sự cố độc lập; stack trace và cause có thể không còn phản ánh đúng bối cảnh từng lần.
- Tự gọi `fillInStackTrace()` có thể cập nhật dấu vết của đối tượng, nhưng làm thay đổi thông tin chẩn đoán; đa số ứng dụng không cần thực hiện thủ công.

Đặc biệt, *message*, *cause* và *stack trace* là ba thứ riêng biệt: message giải thích nội dung lỗi; cause liên kết nguồn gốc; stack trace giúp định vị đường gọi tại điểm ghi nhận.

## 9. Constructor và checked exception: phân loại không phụ thuộc tham số

Hai lệnh:

~~~java
new java.io.IOException("Không đọc được");
new IllegalStateException("Đối tượng chưa sẵn sàng");
~~~

đều chỉ **tạo đối tượng**. Khi ném:

~~~java
static void checkedExample() throws java.io.IOException {
    throw new java.io.IOException("Không đọc được");
}

static void uncheckedExample() {
    throw new IllegalStateException("Đối tượng chưa sẵn sàng");
}
~~~

Đây là hai phương thức minh họa. `checkedExample` khai báo `throws IOException` vì `IOException` là checked; `uncheckedExample` không bị buộc khai báo vì `IllegalStateException` là unchecked.

Thay constructor từ `(String)` sang `(String, Throwable)` **không làm thay đổi** ngoại lệ checked thành unchecked hay ngược lại. Đó là đặc điểm của **lớp ngoại lệ**, được quyết định bởi quan hệ kế thừa với `RuntimeException` và `Error`.

## 10. Các lỗi phổ biến khi tạo ngoại lệ

### Quên `throw`

~~~java
if (age < 0) {
    new IllegalArgumentException("age không được âm");
}
~~~

Đây là **phản ví dụ**: chương trình tạo đối tượng rồi bỏ qua; nó không ném ngoại lệ. Cách đúng:

~~~java
if (age < 0) {
    throw new IllegalArgumentException("age không được âm");
}
~~~

Hai đoạn giả định biến `age` đã tồn tại và nằm trong một phương thức.

### Dùng constructor không tồn tại

Không phải mọi kiểu ngoại lệ đều có đủ bốn overload chuẩn. Constructor không được kế thừa, nên `new SomeException("message", cause)` chỉ hợp lệ khi **lớp `SomeException` thực sự khai báo constructor tương ứng**. Với lớp tùy biến, hãy định nghĩa overload và gọi `super` phù hợp.

### Bỏ mất cause khi chuyển đổi ngoại lệ

~~~java
catch (java.io.IOException e) {
    throw new IllegalStateException("Không tải được dữ liệu");
}
~~~

Đoạn minh họa trên đánh mất liên kết cause. Khi thích hợp, nên viết:

~~~java
catch (java.io.IOException e) {
    throw new IllegalStateException("Không tải được dữ liệu", e);
}
~~~

Đây là các **đoạn `catch` minh họa** gắn với một `try` thực sự có thể ném `IOException`. Giữ cause giúp đọc được quan hệ `Caused by` khi in stack trace.

### Dùng ngoại lệ quá chung chung

~~~java
throw new RuntimeException("Sai");
~~~

Câu lệnh hợp lệ nhưng mơ hồ. Nếu tham số đầu vào không hợp lệ, `IllegalArgumentException` với message cụ thể thường có ích hơn. Nếu thao tác không hợp lệ vì trạng thái đối tượng, cân nhắc `IllegalStateException`. Chọn kiểu tương ứng hợp đồng thay vì lúc nào cũng dùng `Exception` hoặc `RuntimeException`.

### Đưa bí mật vào message

Thông điệp nên cung cấp ngữ cảnh có ích nhưng không phải nơi để chép token, mật khẩu hoặc dữ liệu riêng tư. Đọc log cũng là một phần của mô hình bảo mật ứng dụng.

### Tạo ngoại lệ một lần và ném đi ném lại không kiểm soát

Mỗi sự cố mới thường nên có đối tượng ngoại lệ riêng để phản ánh đúng bối cảnh và nguyên nhân. Dùng lại cùng một đối tượng ở nhiều vị trí có thể gây hiểu nhầm về stack trace, cause và lịch sử sự cố, đặc biệt giữa nhiều luồng.

### Nhầm cause với kiểu ngoại lệ đang được ném

~~~java
RuntimeException wrapper =
        new IllegalStateException(
                "Lỗi miền", new java.io.IOException("Lỗi I/O"));
throw wrapper;
~~~

Đoạn này ném một `IllegalStateException`. `catch (IOException e)` **không** tự động nhận nó, dù `getCause()` trả về `IOException`. Trình xử lý so khớp kiểu ngoại lệ hiện đang truyền. Muốn xử lý nguồn gốc, tầng phù hợp có thể xem `getCause()` hoặc thiết kế API khác.

### Gọi `initCause` sau constructor đã khởi tạo cause

~~~java
Exception e = new Exception("Lỗi", null);
e.initCause(new java.io.IOException("Nguyên nhân mới"));
~~~

Đây là **phản ví dụ**: constructor `Exception(String, Throwable)` đã khởi tạo trạng thái cause dù tham số là `null`. Lệnh `initCause` sau đó ném `IllegalStateException`. Tránh kiểu khởi tạo hai bước khi đã có constructor nhận cause.

## 11. Tiêu chí chọn constructor phù hợp

| Nhu cầu | Cách chọn thường dùng | Mục đích |
| --- | --- | --- |
| Lỗi mới, mô tả trực tiếp | `new IllegalArgumentException("...")` | Cung cấp message cho điều kiện vi phạm |
| Chuyển đổi từ ngoại lệ thấp tầng | `new IllegalStateException("...", cause)` | Giữ cause và bổ sung ngữ cảnh |
| Đã có đối tượng ngoại lệ | `throw e;` | Ném tiếp cùng đối tượng khi hợp đồng cho phép |
| API checked exception riêng | Lớp `extends Exception` với `super(message, cause)` | Người gọi phải xét nghĩa vụ catch/throws |
| API unchecked exception riêng | Lớp `extends RuntimeException` với `super(message, cause)` | Biểu diễn lỗi không có nghĩa vụ bắt/khai báo |
| Cần lưu thêm mã lỗi có cấu trúc | Constructor tùy biến nhận `code` và chuyển message/cause qua `super` | Máy xử lý theo mã ổn định; người đọc xem message |

Nếu lớp lỗi riêng cần trường mã lỗi, có thể viết:

~~~java
public final class ValidationException extends RuntimeException {
    private final String code;

    public ValidationException(
            String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
~~~

Đây là **lớp độc lập** nếu lưu trong `ValidationException.java`. Cách dùng:

~~~java
throw new ValidationException(
        "INVALID_AGE", "Tuổi không hợp lệ", originalCause);
~~~

Dòng sử dụng là minh họa trong phạm vi có biến `originalCause` thích hợp. Hợp đồng có thể kiểm tra `getCode()` thay vì so sánh chuỗi `getMessage()`, bởi message có thể thay đổi.

## 12. Câu hỏi tự kiểm tra

1. Điểm khác nhau giữa `new IllegalArgumentException("x")` và `throw new IllegalArgumentException("x");` là gì?
2. Tạo `new IOException("x")` nhưng không ném thì có bắt buộc khai báo `throws IOException` không?
3. Khi nào bạn dùng constructor `(String message, Throwable cause)` thay vì chỉ `(String message)`?
4. Tại sao constructor lớp ngoại lệ riêng nên gọi `super(message, cause)` khi được truyền hai tham số?
5. Constructor có được kế thừa từ `RuntimeException` sang lớp con một cách tự động không?
6. Một `IllegalStateException` có cause là `IOException` sẽ được `catch (IOException e)` bắt trực tiếp không?
7. Stack trace thông thường gắn với thời điểm tạo ngoại lệ hay chỉ xuất hiện lần đầu khi `throw`?
8. Điều gì xảy ra nếu gọi `initCause` trên ngoại lệ đã được khởi tạo cause bằng constructor nhận `(String, Throwable)`?
9. Vì sao một API nên tránh để caller suy luận loại lỗi bằng cách so sánh chuỗi message?
10. Khi nào cần tạo một lớp ngoại lệ riêng thay vì sử dụng `IllegalArgumentException` hoặc `IllegalStateException`?

**Đáp án tóm tắt:** (1) `new` tạo đối tượng, `throw` ném đối tượng; (2) không, nghĩa vụ checked phát sinh khi ném/gọi mã có thể ném; (3) khi bọc lỗi và cần giữ nguyên nhân; (4) để khởi tạo message/cause tại `Throwable`; (5) không; (6) không, vì đối tượng đang truyền là `IllegalStateException`; (7) thông thường khi khởi tạo đối tượng `Throwable`; (8) ném `IllegalStateException`; (9) vì message không phải hợp đồng máy đọc ổn định; (10) khi cần ngữ nghĩa lỗi, mã lỗi hoặc hợp đồng API riêng.

**Ghi nhớ:** **Constructor tạo và khởi tạo thông tin của một đối tượng ngoại lệ; lệnh `throw` mới phát sinh đường truyền ngoại lệ.** Chọn overload phù hợp để cung cấp message hữu ích, giữ cause khi bọc lỗi và dùng đúng kiểu checked/unchecked theo hợp đồng phương thức.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="throw-statement-003">Phân biệt tạo ngoại lệ với ném ngoại lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt tạo ngoại lệ với ném ngoại lệ trong Java

Trong Java, **tạo một đối tượng ngoại lệ** và **ném đối tượng đó** là hai hành động khác nhau. Hai hành động thường xuất hiện chung trong câu lệnh `throw new ...` nên dễ bị nhầm là một. Phân biệt chúng giúp bạn đọc đúng luồng thực thi, hiểu tại sao có đối tượng `Exception` mà chương trình vẫn tiếp tục chạy bình thường, vì sao một đối tượng có thể được ném lại, và vì sao stack trace đôi khi ghi điểm tạo ngoại lệ thay vì dòng `throw`.

Bài học tập trung vào **lệnh `throw`**, thời điểm đối tượng ngoại lệ được tạo, cách đối tượng ấy đi vào cơ chế xử lý ngoại lệ, các ảnh hưởng đến checked exception và thông tin chẩn đoán.

## 1. Hai hành động có ý nghĩa khác nhau

| Hoạt động | Ví dụ | Kết quả tức thời |
| --- | --- | --- |
| **Tạo ngoại lệ** | `new IllegalArgumentException("Sai tuổi")` | Tạo đối tượng Java thuộc một lớp con `Throwable`; bản thân biểu thức này **không làm phương thức thoát vì ngoại lệ vừa tạo**. |
| **Giữ ngoại lệ trong biến** | `IllegalArgumentException ex = new IllegalArgumentException("Sai tuổi");` | Biến `ex` tham chiếu tới đối tượng; các câu lệnh tiếp theo vẫn chạy bình thường. |
| **Ném ngoại lệ đã có** | `throw ex;` | Kích hoạt luồng điều khiển ngoại lệ bằng đối tượng `ex`; câu lệnh `throw` kết thúc bất thường và tìm bộ xử lý phù hợp. |
| **Tạo rồi ném ngay** | `throw new IllegalArgumentException("Sai tuổi");` | Đánh giá `new` để tạo đối tượng, sau đó `throw` đối tượng vừa tạo. |
| **Khai báo khả năng ném** | `void read() throws IOException` | Mô tả hợp đồng của phương thức ở mức biên dịch; **không tự tạo và không tự ném** ngoại lệ. |

Các lớp như `IOException`, `IllegalArgumentException` hoặc `RuntimeException` cuối cùng đều là lớp con của `java.lang.Throwable`. Từ góc độ ngôn ngữ, ngoại lệ là một đối tượng có kiểu, tham chiếu, thông điệp, nguyên nhân và thông tin stack trace. Tính chất đặc biệt xuất hiện khi **đối tượng đó được ném**.

Ví dụ:

~~~java
// Đoạn minh họa trong một phương thức.
IllegalArgumentException problem =
        new IllegalArgumentException("Số tuổi âm");

System.out.println("Đã tạo ngoại lệ");
System.out.println(problem.getMessage());
System.out.println("Vẫn chạy bình thường");
~~~

Đầu ra khi đoạn trên được thực thi:

~~~text
Đã tạo ngoại lệ
Số tuổi âm
Vẫn chạy bình thường
~~~

Không có dòng `throw`, vì vậy `problem` vẫn chỉ là một đối tượng. Việc đọc `getMessage()`, truyền đối tượng sang phương thức khác, hoặc trả về đối tượng ngoại lệ cũng **không tương đương với ném** nó.

**Lưu ý về tính chính xác:** biểu thức `new` vẫn có thể không hoàn thành bình thường nếu constructor hoặc phần khởi tạo của lớp phát sinh lỗi khác; thông thường, câu `new IOException("...")` thành công sẽ chỉ tạo đối tượng. Không được suy ra rằng mọi lệnh `new` đều tuyệt đối không thể ném bất kỳ ngoại lệ nào.

## 2. Java thực hiện `throw new ...` theo thứ tự nào?

Xem câu lệnh:

~~~java
throw new IllegalStateException("Không kết nối được");
~~~

Có hai giai đoạn về mặt ngữ nghĩa:

1. **Đánh giá biểu thức bên phải `throw`:** Java khởi tạo đối tượng `IllegalStateException` với chuỗi thông điệp đã cho (và thực hiện logic constructor tương ứng).
2. **Thực hiện câu lệnh `throw`:** nếu biểu thức trả về một tham chiếu đối tượng ngoại lệ khác `null`, Java ném đối tượng đó; câu lệnh kết thúc bất thường, làm rời luồng thực thi bình thường.

Hai dòng dưới đây có hành vi ném cùng một kiểu ngoại lệ nếu quá trình tạo hoàn tất:

~~~java
// Cách 1: tạo và ném trong cùng một câu lệnh.
throw new IllegalStateException("Không kết nối được");
~~~

~~~java
// Cách 2: tạo trước rồi ném đối tượng có sẵn.
// Hai dòng nằm trong một phương thức.
IllegalStateException error =
        new IllegalStateException("Không kết nối được");
throw error;
~~~

Tuy nhiên, **thời điểm tạo** có thể khác. Ở cách 2, có thể có xử lý khác giữa `new` và `throw`; đối tượng ngoại lệ có thể đã được truyền sang nơi khác, được lưu và được ghi nhận stack trace từ trước. Một đối tượng mới ở mỗi lần `throw new` cũng khác với việc ném lại cùng một đối tượng đã tồn tại.

## 3. Chương trình Java 17+ hoàn chỉnh: tạo trước, ném sau

Tạo tệp `CreateVsThrowDemo.java`:

~~~java
public class CreateVsThrowDemo {
    static IllegalArgumentException prepare() {
        System.out.println("1. Tạo ngoại lệ");
        return new IllegalArgumentException("tuổi phải >= 0");
    }

    static void validate(int age, IllegalArgumentException prepared) {
        System.out.println("3. Bắt đầu kiểm tra");
        if (age < 0) {
            throw prepared;
        }
        System.out.println("Tuổi hợp lệ");
    }

    public static void main(String[] args) {
        IllegalArgumentException prepared = prepare();
        System.out.println("2. Đã tạo, chương trình vẫn chạy");
        try {
            validate(-1, prepared);
            System.out.println("Không in dòng này");
        } catch (IllegalArgumentException ex) {
            System.out.println("4. Đã bắt: " + ex.getMessage());
            System.out.println("5. Cùng đối tượng: " + (ex == prepared));
        }
        System.out.println("6. Kết thúc bình thường");
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 CreateVsThrowDemo.java
java CreateVsThrowDemo
~~~

**Đầu ra chính xác:**

~~~text
1. Tạo ngoại lệ
2. Đã tạo, chương trình vẫn chạy
3. Bắt đầu kiểm tra
4. Đã bắt: tuổi phải >= 0
5. Cùng đối tượng: true
6. Kết thúc bình thường
~~~

Phân tích từng thời điểm:

1. `prepare()` tạo `IllegalArgumentException` và **trả về chính đối tượng ngoại lệ**. Việc trả về một `Throwable` hợp lệ về ngôn ngữ và không làm chương trình ném ngoại lệ.
2. `main()` vẫn thực thi lệnh in số 2, chứng minh chương trình chưa chuyển sang xử lý ngoại lệ.
3. `validate(-1, prepared)` bắt đầu; điều kiện `age < 0` đúng nên thực hiện `throw prepared;`.
4. `validate()` kết thúc bất thường tại `throw`. Câu `System.out.println("Tuổi hợp lệ")` không chạy; câu in `Không in dòng này` tại `main()` cũng bị bỏ qua.
5. Khối `catch (IllegalArgumentException ex)` trong `main()` nhận **đúng đối tượng đã chuẩn bị**: biểu thức `ex == prepared` cho ra `true`.
6. Vì `catch` đã xử lý và hoàn thành bình thường, chương trình tiếp tục sau cấu trúc `try/catch`.

Nếu đổi `validate(-1, prepared)` thành `validate(18, prepared)`, nhánh `throw` không chạy; ngoại lệ đã tạo vẫn chỉ là đối tượng. Điều này cho thấy **tạo đối tượng không đồng nghĩa với một lỗi thực sự đã phát sinh**.

## 4. `throw` thay đổi luồng điều khiển; `new` thông thường thì không

Các đoạn sau có ý nghĩa khác nhau:

~~~java
// A. Tạo đối tượng và bỏ tham chiếu trả về.
new IllegalArgumentException("Sai dữ liệu");
System.out.println("Vẫn đến đây");
~~~

~~~java
// B. Ném, nếu đoạn được đặt trong một phương thức.
throw new IllegalArgumentException("Sai dữ liệu");
~~~

Trường hợp A biên dịch khi đặt trong phương thức phù hợp và vẫn in `Vẫn đến đây`. Trường hợp B làm phương thức kết thúc bất thường nếu không có cơ chế xử lý ngay tại ngữ cảnh bao quanh.

Một ví dụ khác minh họa lệnh sau `throw` chỉ bị bỏ qua ở **nhánh thực sự được chọn**:

~~~java
// Đoạn minh họa trong một phương thức.
if (age < 0) {
    throw new IllegalArgumentException("Tuổi âm");
}
System.out.println("Đã qua bước kiểm tra");
~~~

Với `age < 0`, câu in không được thực thi. Với `age >= 0`, không có `throw` nên câu in sẽ chạy bình thường.

Nếu đặt một câu lệnh thông thường ngay sau một `throw` vô điều kiện trong cùng khối mà không có nhánh khác, trình biên dịch có thể báo `unreachable statement`:

~~~java
// Minh họa sai; KHÔNG biên dịch khi đặt trong phương thức.
throw new IllegalArgumentException("Lỗi");
System.out.println("Không thể tới đây");
~~~

Khi ném ngoại lệ, Java tìm `catch` phù hợp trong những khối `try` bao quanh động. Nếu không có, ngoại lệ tiếp tục đi lên các lời gọi đang hoạt động. Nhưng đây là hệ quả của **`throw`**, không phải của việc tạo đối tượng.

## 5. `throw` phải nhận kiểu gì?

Theo Java Language Specification, toán hạng của câu lệnh `throw` phải là một biểu thức có kiểu tham chiếu tương thích với `Throwable`; trường hợp ném tham chiếu `null` được giải quyết theo quy tắc runtime riêng.

Ví dụ hợp lệ:

~~~java
throw new IllegalArgumentException("Không hợp lệ");
~~~

~~~java
// Đoạn minh họa trong phương thức có biến Throwable problem.
throw problem;
~~~

Trường hợp không hợp lệ:

~~~java
// Không biên dịch: String không phải một Throwable.
throw new String("Lỗi");
~~~

Bạn không thể dùng `throw 5;`, `throw "Lỗi";` hoặc `throw new Object();` để kích hoạt xử lý ngoại lệ. Phải ném đối tượng thuộc một lớp con của `Throwable`, ví dụ một lớp ngoại lệ tùy chỉnh kế thừa `Exception` hoặc `RuntimeException`.

**Khi biểu thức của `throw` có giá trị `null`:**

~~~java
// Đoạn minh họa trong một phương thức.
RuntimeException problem = null;
throw problem;
~~~

Đoạn trên **biên dịch**; lúc chạy, thực hiện `throw` với tham chiếu `null` gây `NullPointerException`. Đây không phải “không ném gì”; cũng không phải chương trình rơi qua dòng `throw`.

Để kiểm soát lỗi rõ ràng, bạn nên bảo đảm tham chiếu cần ném khác `null`, hoặc trực tiếp tạo ngoại lệ phù hợp với tình huống.

## 6. Đừng nhầm `throw` với `throws`

Hai từ khóa chỉ khác một chữ cái nhưng phục vụ hai mục đích:

- `throw expression;` là **câu lệnh thực thi**: ném một đối tượng ngoại lệ được đánh giá từ biểu thức.
- `throws Type1, Type2` là **khai báo trong chữ ký phương thức/constructor**: mô tả các loại ngoại lệ kiểm tra (checked) mà phương thức có thể để thoát ra, đồng thời có thể liệt kê unchecked nếu muốn.

Ví dụ hợp lệ về checked exception:

~~~java
import java.io.IOException;

// Đoạn minh họa đặt bên trong một lớp.
static void failRead() throws IOException {
    IOException failure = new IOException("Không đọc được tệp");
    throw failure;
}
~~~

Ở đây:

1. `new IOException(...)` chỉ tạo đối tượng `IOException`, dù bản thân kiểu của nó là checked exception.
2. `throw failure;` **ném checked exception**, nên phương thức phải bắt hoặc khai báo `IOException` (hoặc kiểu cha thích hợp).
3. `throws IOException` trên chữ ký thông báo yêu cầu đó cho người gọi. Người gọi `failRead()` lại phải bắt hoặc khai báo khi cần.

Nhưng phương thức sau **biên dịch được mà không cần `throws IOException`**:

~~~java
import java.io.IOException;

// Đoạn minh họa đặt bên trong một lớp.
static IOException prepareFailure() {
    return new IOException("Không đọc được tệp");
}
~~~

Bởi vì nó **trả về một đối tượng** `IOException`, không ném đối tượng ấy. Kiểu trả về `IOException` không có nghĩa phương thức sẽ kết thúc bất thường.

Ví dụ người gọi:

~~~java
// Đoạn minh họa trong một phương thức.
IOException prepared = prepareFailure();
System.out.println(prepared.getMessage()); // Chỉ đọc thông điệp.
~~~

Người gọi không phải khai báo `throws IOException` chỉ vì nhận một đối tượng có lớp là checked exception. Quy tắc bắt hoặc khai báo áp dụng cho **khả năng ngoại lệ checked thoát ra**, không phải cho mọi nơi khởi tạo biến có kiểu `Exception`.

## 7. Bắt và ném lại: cùng đối tượng hay đối tượng mới?

`catch` nhận tham chiếu đến ngoại lệ đã được ném. Bạn có thể:

1. Xử lý và kết thúc khối `catch` bình thường — quá trình lan truyền của ngoại lệ đó kết thúc.
2. Ném lại chính đối tượng với `throw ex;`.
3. Tạo ngoại lệ mới và ném bằng `throw new ...;`, thường giữ ngoại lệ cũ làm **cause**.

Ví dụ cùng đối tượng:

~~~java
// Đoạn minh họa trong phương thức tương thích với unchecked exception.
try {
    performTask();
} catch (IllegalStateException ex) {
    System.err.println("Gặp sự cố trong performTask");
    throw ex; // Ném lại cùng tham chiếu, không tạo đối tượng mới.
}
~~~

Ném lại bằng `throw ex` không tự thêm một lớp nguyên nhân `Caused by`; đó vẫn là cùng một đối tượng `Throwable`. Nếu tầng trên bắt lại, đối chiếu tham chiếu bằng `==` vẫn có thể cho kết quả `true`.

Ngược lại:

~~~java
// Đoạn minh họa trong phương thức tương thích với unchecked exception.
try {
    performTask();
} catch (IllegalStateException ex) {
    throw new IllegalStateException("Tác vụ không hoàn thành", ex);
}
~~~

Câu `new IllegalStateException(..., ex)` tạo một **đối tượng khác** và thiết lập `ex` làm nguyên nhân. Câu `throw` ném đối tượng mới này; `getCause()` giúp truy đến ngoại lệ cũ. Khi ghi stack trace, thường có phần `Caused by:` thể hiện nguyên nhân gốc.

Không nên cứ bắt một ngoại lệ rồi tạo mới mà **không truyền `cause`**, vì như vậy dễ làm mất đường dẫn chẩn đoán tới lỗi ban đầu.

Với checked exception, quy tắc biên dịch vẫn áp dụng khi ném lại: phương thức phải khai báo hoặc xử lý các loại checked exception có thể thoát ra. Java có **precise rethrow analysis** trong một số trường hợp `catch` từ Java 7, cho phép compiler suy luận kiểu checked được ném lại chính xác hơn; điều này không thay đổi khác biệt nền tảng giữa tạo và ném.

## 8. Stack trace gắn với thời điểm nào?

Một điểm tinh tế: với các constructor `Throwable` thông thường, **stack trace được ghi nhận khi đối tượng ngoại lệ được tạo**, không phải tự động chụp lại tại từng dòng `throw`. Vì vậy nếu bạn tạo ở phương thức A và sau đó ném ở phương thức B, stack trace mặc định có thể bắt đầu bằng A.

Chương trình độc lập `TraceCreationDemo.java`:

~~~java
public class TraceCreationDemo {
    static IllegalStateException createProblem() {
        return new IllegalStateException("sự cố");
    }

    static void throwProblem(IllegalStateException problem) {
        throw problem;
    }

    public static void main(String[] args) {
        IllegalStateException problem = createProblem();
        try {
            throwProblem(problem);
        } catch (IllegalStateException ex) {
            System.out.println("Cùng đối tượng: " + (problem == ex));
            System.out.println("Frame đầu: " + ex.getStackTrace()[0].getMethodName());
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 TraceCreationDemo.java
java TraceCreationDemo
~~~

Stdout với đối tượng `IllegalStateException` thông thường:

~~~text
Cùng đối tượng: true
Frame đầu: createProblem
~~~

**Giải thích:** `createProblem()` đã xây dựng ngoại lệ và ghi nhận stack trace; `throwProblem()` chỉ ném tham chiếu có sẵn. JVM không tự thay thế trace bằng đường gọi đến `throwProblem()` mỗi lần đối tượng được ném.

Các trường hợp cần biết:

- `throw new IllegalStateException(...)` thực hiện tạo gần sát điểm ném nên vị trí đầu trace thường trùng với câu lệnh `throw new`.
- `throw oldException;` có thể giữ trace từ lúc `oldException` được tạo trước đó. Việc dùng một ngoại lệ tái sử dụng để ném nhiều lần thường tạo thông tin vị trí lỗi thiếu chính xác cho những lần sau.
- `Throwable.fillInStackTrace()` có thể cập nhật thông tin trace theo ngăn xếp hiện tại; đây là thao tác tường minh, không phải điều xảy ra mặc định ở mỗi `throw`.
- Một số loại `Throwable` tùy chỉnh có thể vô hiệu hóa writable stack trace, hoặc ứng dụng chủ động thay đổi stack trace qua API. Vì vậy ví dụ về phần tử đầu trong `getStackTrace()` nói trên giả định đối tượng Java thông thường với stack trace khả dụng.

Trong thiết kế ứng dụng, thường nên **tạo ngoại lệ khi thực sự phát hiện lỗi**, để trace phản ánh vị trí có ý nghĩa. Chỉ tách riêng khâu tạo và ném khi có lý do rõ ràng; đừng tạo sẵn một ngoại lệ dùng chung cho nhiều yêu cầu hay luồng rồi mong stack trace thể hiện đúng từng sự kiện lỗi.

## 9. Tại sao đôi khi chỉ cần tạo, không nên ném?

Một ngoại lệ có thể được tạo hoặc lưu trữ như một đối tượng dữ liệu trong các tình huống chuyên biệt:

- Tầng code chuẩn bị ngữ cảnh cho lỗi rồi chuyển đối tượng cho tầng khác lựa chọn có ném hay không.
- Một kết quả bất đồng bộ có thể **lưu một đối tượng ngoại lệ** dưới dạng nguyên nhân thất bại. Bên nhận quan sát trạng thái thất bại hoặc một ngoại lệ bọc khi lấy kết quả; không nên đánh đồng thời điểm lưu đối tượng với thời điểm một luồng khác ném ngoại lệ.
- Một API chẩn đoán có thể giữ `Throwable` để ghi lại ngăn xếp tại thời điểm nhất định, nhưng việc tạo như vậy có chi phí và phải có mục đích.

Dù hợp lệ, việc `return new IOException(...)` thay cho ném ngoại lệ thường **không phải phong cách xử lý lỗi Java thông thường**. Nó có thể làm người gọi nhầm rằng thao tác đã thành công. Nếu API có ý định trả lỗi như dữ liệu, hợp đồng nên nói rõ kiểu kết quả và cách sử dụng, thay vì dùng `Exception` lẫn với dữ liệu thành công.

Ở chiều ngược lại, một phương thức khai báo `throws` cũng không nhất thiết ném ngoại lệ trong mỗi lượt gọi. `throws` cho biết khả năng, còn lệnh `throw` chỉ chạy khi đường thực thi thật sự đi đến đó.

## 10. Ngoại lệ tùy chỉnh: constructor không thay thế cho `throw`

Có thể tự định nghĩa lớp ngoại lệ bằng cách kế thừa `Exception` (checked) hoặc `RuntimeException` (unchecked):

~~~java
// Đoạn minh họa là lớp Java độc lập.
class InvalidOrderException extends Exception {
    InvalidOrderException(String message) {
        super(message);
    }
}
~~~

Hai thao tác sau có kết quả khác nhau:

~~~java
// Đoạn minh họa trong một phương thức.
InvalidOrderException problem = new InvalidOrderException("Đơn hàng trống");
// Chỉ tạo và lưu; không phát sinh luồng xử lý ngoại lệ.
~~~

~~~java
// Đoạn minh họa trong phương thức có throws InvalidOrderException.
throw new InvalidOrderException("Đơn hàng trống");
// Phương thức kết thúc bất thường và người gọi phải bắt hoặc khai báo.
~~~

Constructor `InvalidOrderException(String)` chỉ chuẩn bị trạng thái đối tượng và gọi `super(message)`. Bản thân `extends Exception` không khiến câu `new InvalidOrderException(...)` tự động bị ném. Cũng không cần tự thêm `throw this` trong constructor; đó không phải cách đúng để định nghĩa lớp ngoại lệ.

Đặt tên ngoại lệ theo thất bại có ý nghĩa và sử dụng checked/unchecked nhất quán với hợp đồng API. Bất kể loại checked hay unchecked, việc **khởi tạo thành công** vẫn khác với việc **ném**.

## 11. Lưu ý khi thiết kế và chẩn đoán lỗi

| Sai lầm phổ biến | Hệ quả | Cách hiểu/sửa |
| --- | --- | --- |
| Chỉ viết `new Exception("Lỗi");` và tin rằng chương trình báo lỗi | Đối tượng được tạo rồi có thể bị bỏ qua; luồng chạy tiếp. | Dùng `throw new ...` khi cần kích hoạt luồng ngoại lệ. |
| Dùng `return new Exception(...)` và tin rằng người gọi phải `catch` | Trả về giá trị thường không đòi `catch`. | Chỉ trả lỗi như dữ liệu khi API quy định rõ; nếu là checked failure, dùng `throw`/`throws`. |
| Nhầm `throw` với `throws` | Khai báo chữ ký nhưng mong runtime tự ném. | `throw` là câu lệnh, `throws` là khai báo. |
| Ném `String`, `Object` hoặc kiểu số | Lỗi biên dịch do không tương thích `Throwable`. | Dùng đối tượng thuộc lớp con của `Throwable`. |
| `throw null` và nghĩ là bỏ qua ném | Gây `NullPointerException` tại runtime. | Bảo đảm tham chiếu khác `null` hoặc tạo lỗi phù hợp. |
| Dùng chung một ngoại lệ tạo từ trước rồi ném nhiều lần | Trace có thể chỉ tới thời điểm tạo, gây khó tìm vị trí lần thất bại mới. | Thông thường tạo ngoại lệ mới tại điểm lỗi xảy ra. |
| Bắt lỗi cũ rồi `throw new ...` nhưng bỏ `cause` | Làm mất quan hệ nguyên nhân cần thiết để debug. | Dùng constructor `(message, cause)` khi bọc. |
| Ghi log rồi `throw` ở mọi tầng | Một lỗi có thể tạo nhiều log trùng và nhiễu. | Chuyển tiếp hợp lý, ghi log tại ranh giới giám sát phù hợp. |
| Đưa câu lệnh thường ngay sau `throw` vô điều kiện | Có thể nhận lỗi `unreachable statement`. | Tổ chức lại nhánh điều kiện và đường trả về. |

Một lưu ý về hiệu năng: tạo một đối tượng `Throwable` thông thường có thể phải thu thập stack trace nên **không nên lạm dụng chỉ để điều khiển luồng bình thường**. Khi đầu vào không hợp lệ thật sự là tình huống thất bại theo hợp đồng, ném ngoại lệ rõ ràng là hợp lý; với các kết quả có thể dự đoán và diễn ra thường xuyên, cân nhắc kiểu trả về hoặc điều kiện kiểm tra phù hợp.

## 12. Tự kiểm tra kiến thức

**Câu 1.** Với đoạn `IOException e = new IOException("x");`, phương thức có bắt buộc ghi `throws IOException` không?

**Trả lời:** Không, nếu chỉ tạo đối tượng và không có lời gọi nào khác ném checked exception. Việc tạo đối tượng `IOException` không đồng nghĩa với ném nó.

**Câu 2.** `throw e;` và `throw new IOException("x");` có luôn giống hệt nhau không?

**Trả lời:** Cả hai đều ném một đối tượng nếu thành công, nhưng câu đầu ném lại đối tượng đang được tham chiếu, câu sau tạo đối tượng mới trước khi ném. Khác nhau về danh tính đối tượng, thời điểm tạo và thông tin stack trace.

**Câu 3.** Ngoại lệ được tạo ở `make()`, sau đó ném ở `send()`. Stack trace mặc định có thể bắt đầu tại đâu?

**Trả lời:** Với `Throwable` thông thường, tại `make()` — nơi đối tượng được tạo, trừ khi trace được làm mới hoặc thay đổi.

**Câu 4.** Nếu `throw new IllegalArgumentException(...)` chạy, câu lệnh bình thường ngay sau nó trong cùng nhánh có chạy không?

**Trả lời:** Không. `throw` kết thúc bất thường, trừ khi trình tự điều khiển đã xử lý ở một ngữ cảnh bao quanh phù hợp và chương trình tiếp tục từ điểm sau cấu trúc xử lý.

**Câu 5.** Ném tham chiếu `null` sẽ làm gì?

**Trả lời:** `throw null` gây `NullPointerException` tại runtime.

**Câu 6.** `catch (Exception ex) { throw ex; }` có tạo ngoại lệ mới không?

**Trả lời:** Không; đó là cùng đối tượng `ex`. Với checked exception, compiler còn áp dụng các quy tắc về `throws` và có thể có phân tích ném lại chính xác.

**Câu 7.** Muốn tạo ngữ cảnh lỗi mới nhưng giữ lỗi gốc, nên viết gì?

**Trả lời:** Dùng constructor nhận nguyên nhân, ví dụ `throw new IllegalStateException("Nạp cấu hình thất bại", ex);`.

## Tài liệu tham khảo

- [Java Language Specification 17, §14.18 — The `throw` Statement](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.18): biểu thức được ném, ngữ nghĩa ném `null`, kết thúc bất thường.
- [Java Language Specification 17, §11.2 — Compile-Time Checking of Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.2): checked exception và quy tắc bắt hoặc khai báo.
- [Java Language Specification 17, §15.9 — Class Instance Creation Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.9): ngữ nghĩa tạo đối tượng bằng `new`.
- [Java SE 17 API — `Throwable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html): constructor, cause, stack trace, `fillInStackTrace()`.
- [Java SE 17 API — `IllegalArgumentException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/IllegalArgumentException.html): ngoại lệ unchecked minh họa.

**Điểm cần nhớ:** `new SomeException(...)` tạo một đối tượng mang thông tin lỗi; `throw exception;` mới dùng đối tượng đó để chuyển luồng điều khiển sang cơ chế xử lý ngoại lệ. `throw new SomeException(...);` kết hợp cả hai, trong khi `throws SomeException` chỉ khai báo hợp đồng phương thức.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="throw-statement-004">Ném ngoại lệ có thông điệp và ngữ cảnh phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Ném ngoại lệ có thông điệp và ngữ cảnh phù hợp trong Java

Lệnh `throw` thông báo rằng thao tác **không thể tiếp tục như hợp đồng đã cam kết**. Tuy nhiên, chỉ ném đúng loại ngoại lệ vẫn chưa đủ: nếu thông điệp quá chung chung, thiếu thông tin về thao tác và nguyên nhân, người đọc log hoặc người gọi API sẽ rất khó xác định phải sửa điều gì.

Ví dụ:

```java
throw new IllegalArgumentException("Invalid");
```

Người đọc chỉ biết “không hợp lệ”, nhưng không biết **tham số nào**, **quy tắc nào** hoặc **đang thực hiện thao tác nào**. Thông điệp tốt hơn là:

```java
throw new IllegalArgumentException(
        "field=quantity: must be a positive integer");
```

Đây là **các câu lệnh minh họa**, không phải chương trình độc lập. Với dữ liệu đầu vào sai, câu thứ hai cho người xử lý biết tên trường và điều kiện vi phạm mà không phải tiết lộ giá trị đầu vào.

Nguyên tắc xuyên suốt: **chọn kiểu ngoại lệ đúng bản chất lỗi; viết thông điệp mô tả thất bại có thể hành động; đưa ngữ cảnh cần thiết ở tầng có đủ thông tin; giữ nguyên nhân gốc khi chuyển ngoại lệ; hạn chế dữ liệu nhạy cảm**.

## 1. `throw` làm gì và thông điệp đóng vai trò gì?

Trong Java, `throw` là câu lệnh ném một giá trị có kiểu `Throwable` hoặc một lớp con; thực tế ta thường ném một đối tượng `Exception` hay `RuntimeException` cụ thể:

```java
static int requirePositive(int value) {
    if (value <= 0) {
        throw new IllegalArgumentException(
                "value must be greater than zero");
    }
    return value;
}
```

Đây là một **phương thức minh họa**, cần đặt trong lớp Java để biên dịch. Khi `value <= 0`:

1. Đối tượng `IllegalArgumentException` được tạo với thông điệp `"value must be greater than zero"`.
2. `throw` làm phương thức hoàn tất bất thường tại câu lệnh này; `return` không được chạy.
3. JVM tìm `catch` phù hợp trong chuỗi lời gọi đang hoạt động.
4. Nếu người gọi bắt được ngoại lệ, `ex.getMessage()` cung cấp thông điệp; `ex.getClass()` cung cấp kiểu để phân loại.

**Thông điệp không phải kiểu ngoại lệ.** Một chuỗi có nội dung `"IOException"` bên trong `IllegalArgumentException` không biến ngoại lệ đó thành `IOException`. Handler được chọn theo kiểu thực tế của đối tượng và quan hệ kế thừa, không theo nội dung chuỗi.

Phân biệt ba thành phần:

| Thành phần | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| Kiểu ngoại lệ | Loại thất bại và nhánh `catch` có thể xử lý | `IllegalArgumentException` |
| Thông điệp (`message`) | Mô tả ngắn gọn điều kiện hoặc thao tác thất bại | `"field=quantity: must be positive"` |
| Nguyên nhân (`cause`) | Liên kết tới ngoại lệ gốc khi lỗi được chuyển đổi hoặc bổ sung ngữ cảnh | `NumberFormatException` bên trong `IllegalArgumentException` |

Ngoài ra, đối tượng `Throwable` thường mang stack trace và có thể chứa các ngoại lệ bị *suppressed* trong quá trình dọn tài nguyên. Một lỗi có thể có **message tốt nhưng cause bị mất**, hoặc cause đầy đủ nhưng message thiếu ngữ cảnh; hai vấn đề cần được giải quyết riêng.

## 2. Thông điệp tốt nên trả lời những câu hỏi nào?

Một thông điệp hữu ích thường giúp nhận ra:

- **Điều gì thất bại?** Ví dụ: chuyển đổi trường `quantity`, tải cấu hình, tạo đơn hàng.
- **Điều kiện nào không được đáp ứng?** Ví dụ: phải là số nguyên dương, tệp bắt buộc không tồn tại, trạng thái hiện tại chưa cho phép chuyển bước.
- **Ngữ cảnh nào giúp xác định lần thất bại?** Ví dụ: một mã tác vụ nội bộ không nhạy cảm, tên thao tác, tên trường, tên cấu hình.
- **Người nhận có thể hành động gì?** Ví dụ: sửa trường nhập, bổ sung cấu hình, kiểm tra kết nối. Không phải mọi ngoại lệ đều cần đưa hướng dẫn cho người dùng cuối.

Ví dụ so sánh:

| Thông điệp mơ hồ | Thông điệp có ích hơn | Lý do |
| --- | --- | --- |
| `"Error"` | `"field=port: must be between 1 and 65535"` | Nêu trường và điều kiện |
| `"Bad input"` | `"field=quantity: expected a positive integer"` | Cho biết quy tắc dữ liệu |
| `"Cannot continue"` | `"order transition rejected: expected status DRAFT"` | Nêu trạng thái mong đợi |
| `"Failed"` | `"configuration loading failed: resource=app-settings"` | Nêu thao tác và định danh tài nguyên an toàn |
| `"null"` | `"required dependency 'paymentGateway' was not configured"` | Chỉ ra phụ thuộc bị thiếu |

Các câu trên chỉ minh họa cấu trúc thông điệp; tên trường, giới hạn và trạng thái phải phản ánh **hợp đồng thật** của ứng dụng. Không nên phát minh một quy tắc “1–65535” nếu ứng dụng dùng một tập cổng nhỏ hơn, hay phát biểu “không tồn tại” khi API chỉ báo “không thể đọc”.

### Nên mô tả *sự thật quan sát được*

Nếu `Files.readString(path)` báo `IOException`, chưa chắc nguyên nhân là tệp không tồn tại. Có thể thiếu quyền, lỗi thiết bị hoặc một sự cố I/O khác. Vì vậy thông điệp `"failed to read configuration"` chính xác hơn `"file does not exist"` trừ khi đã xác định đúng loại `NoSuchFileException`.

Tương tự, `NumberFormatException` cho biết không thể diễn giải chuỗi theo cú pháp số được yêu cầu; `Integer.parseInt` còn có thể ném nó khi số vượt khỏi phạm vi `int`. Thông điệp `"must fit into a signed 32-bit integer"` chỉ nên dùng khi hợp đồng cần ràng buộc ấy.

### Dùng từ ngữ ổn định, súc tích

Thông điệp nên đủ ngắn để đọc trong log, đủ cụ thể để phân tích. Có thể nhất quán theo mẫu:

```text
operation=<operation>, field=<field>: <violated condition>
```

hoặc viết câu tự nhiên dễ hiểu. Mẫu chỉ là gợi ý; Java không áp đặt định dạng `message`. Điều quan trọng hơn là **không thay đổi ý nghĩa lỗi qua các tầng**.

## 3. Lỗi ở đâu thì ném ở đó; thông tin ở tầng nào thì bổ sung tại tầng đó

Một hàm chuyển chuỗi thành số biết dữ liệu cần là số nguyên dương; nó **không tự biết** chuỗi đang dùng cho đơn hàng nào. Tầng nghiệp vụ gọi hàm có thể biết mã đơn và tên trường. Phân chia trách nhiệm giúp tránh đẩy quá nhiều tham số chỉ để phục vụ thông báo lỗi.

```text
submitOrder(orderId, rawQuantity)
  └── parseQuantity(rawQuantity)
       ├── biết quy tắc “bắt buộc có dữ liệu”
       ├── biết quy tắc “phải là số nguyên”
       └── biết quy tắc “phải lớn hơn 0”
  └── biết đây là trường quantity của orderId
       └── bổ sung bối cảnh và giữ cause khi chuyển lỗi
```

Nhờ đó:

- Tầng thấp ném một lỗi mô tả **quy tắc bị vi phạm**.
- Tầng gọi thêm **bối cảnh hoạt động** mà nó chắc chắn biết.
- Tầng giao tiếp có thể chuyển lỗi thành phản hồi thân thiện cho người dùng; tầng log có thể lưu thông tin điều tra theo chính sách.

Không phải lúc nào cũng cần bọc ngoại lệ ở từng tầng. Nếu một ngoại lệ đã đủ rõ và người gọi không có thêm thông tin hữu ích, cứ để nó lan truyền sẽ đơn giản hơn.

## 4. Ví dụ Java 17+ hoàn chỉnh: thông điệp, ngữ cảnh và nguyên nhân

Chương trình dưới đây mô phỏng xử lý số lượng đặt hàng, không cần file hay dịch vụ ngoài. Tên đơn hàng trong ví dụ là **định danh giả lập, được coi là an toàn để đưa vào thông điệp**. Trong ứng dụng thực, cần xét tính nhạy cảm của từng định danh.

Lưu thành tệp **`ThrowWithContextDemo.java`**:

```java
public class ThrowWithContextDemo {

    static final class OrderValidationException extends RuntimeException {
        OrderValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static int parseQuantity(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(
                    "quantity is required");
        }

        final int quantity;
        try {
            quantity = Integer.parseInt(raw);
        } catch (NumberFormatException cause) {
            throw new IllegalArgumentException(
                    "quantity must be an integer", cause);
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "quantity must be positive");
        }
        return quantity;
    }

    static void submitOrder(String orderId, String rawQuantity) {
        try {
            int quantity = parseQuantity(rawQuantity);
            System.out.println(
                    "OK order=" + orderId + ", quantity=" + quantity);
        } catch (IllegalArgumentException cause) {
            throw new OrderValidationException(
                    "order=" + orderId
                    + ", field=quantity: " + cause.getMessage(),
                    cause);
        }
    }

    static void runCase(String orderId, String rawQuantity) {
        try {
            submitOrder(orderId, rawQuantity);
        } catch (OrderValidationException ex) {
            System.out.println("ERROR " + ex.getMessage());

            Throwable root = ex;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            System.out.println(
                    "ROOT " + root.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        runCase("A-1", "2");
        runCase("A-2", "abc");
        runCase("A-3", "0");
        runCase("A-4", null);
    }
}
```

Chạy bằng JDK 17 hoặc mới hơn:

```bash
javac -encoding UTF-8 ThrowWithContextDemo.java
java ThrowWithContextDemo
```

**Stdout chính xác**:

```text
OK order=A-1, quantity=2
ERROR order=A-2, field=quantity: quantity must be an integer
ROOT NumberFormatException
ERROR order=A-3, field=quantity: quantity must be positive
ROOT IllegalArgumentException
ERROR order=A-4, field=quantity: quantity is required
ROOT IllegalArgumentException
```

### Phân tích ba kiểu thất bại

**Chuỗi `"abc"`:** `Integer.parseInt("abc")` ném `NumberFormatException`. `parseQuantity` bắt lỗi đó và ném `IllegalArgumentException("quantity must be an integer", cause)`. `submitOrder` bổ sung ngữ cảnh `order=A-2, field=quantity` bằng `OrderValidationException`, vẫn giữ toàn bộ chuỗi nguyên nhân. Tầng ngoài có thể xem message dành cho nghiệp vụ hoặc đi theo `getCause()` để phân tích sâu.

**Chuỗi `"0"`:** chuyển đổi số thành công nhưng `quantity <= 0` sai hợp đồng. Không có `NumberFormatException`; lỗi gốc là `IllegalArgumentException` về điều kiện nghiệp vụ. Message nêu “must be positive”. Vì vậy chỉ dựa vào kiểu gốc `IllegalArgumentException` là chưa đủ để phân biệt mọi quy tắc; thông điệp và/hoặc thuộc tính lỗi có cấu trúc có thể bổ sung ngữ nghĩa.

**`null`:** điều kiện `raw == null || raw.isBlank()` có **short-circuit**, nên khi `raw == null`, lời gọi `raw.isBlank()` không chạy và không phát sinh `NullPointerException`. Thông điệp nêu rõ trường được yêu cầu. Đây là lựa chọn hợp đồng của ví dụ; một API khác có thể ưu tiên `NullPointerException` cho tham số bắt buộc không `null`.

### Điểm cần lưu ý trong cách bọc lỗi

`submitOrder` có nhiệm vụ thêm định danh đơn và trường dữ liệu nên có lý do bọc lỗi. Trong một API lớn, thông thường không nên tạo một wrapper mới ở *mọi* phương thức, vì chuỗi `Caused by` quá dài có thể làm việc chẩn đoán trở nên ồn ào.

Ví dụ cũng **không in rawQuantity khi dữ liệu sai**. Trong mã thực tế, chuỗi đầu vào có thể chứa thông tin không nên đưa vào log. Nếu cần nhận diện lần xử lý cụ thể, hãy dùng mã yêu cầu/tác vụ được cho phép thay vì đưa toàn bộ dữ liệu người dùng vào message.

## 5. Cách giữ cause khi dùng `throw new ...`

Một phương thức bắt lỗi ở tầng thấp có thể muốn chuyển nó sang loại ngoại lệ phù hợp với hợp đồng tầng trên.

### Cách làm thiếu thông tin

```java
try {
    readConfiguration();
} catch (java.io.IOException ex) {
    throw new IllegalStateException("Cannot initialize service");
}
```

Đây là mã **minh họa trong một phương thức**, giả định `readConfiguration()` được định nghĩa và có thể ném `IOException`. Mã này tạo lỗi mới nhưng **không giữ quan hệ cause** tới `IOException` đã bắt. Khi lỗi được điều tra, nguyên nhân gốc có thể bị mất khỏi chuỗi ngoại lệ.

### Cách làm giữ nguyên nhân

```java
try {
    readConfiguration();
} catch (java.io.IOException cause) {
    throw new IllegalStateException(
            "Cannot initialize service: configuration loading failed",
            cause);
}
```

Constructor `(String message, Throwable cause)` của `IllegalStateException` giúp tạo thông điệp ở tầng ứng dụng và bảo toàn `cause`. Caller có thể đọc:

```java
Throwable rootCause = failure.getCause();
```

Câu lệnh này chỉ minh họa; `failure` cần là một biến ngoại lệ có thật, và `getCause()` có thể trả về `null`. Muốn theo toàn bộ chuỗi nguyên nhân, phải duyệt cho đến khi không còn cause; nếu có API nhận dữ liệu không đáng tin hoặc ngoại lệ tùy chỉnh, mã điều tra cũng cần đề phòng chuỗi cause bất thường.

**Một lỗi nên có trách nhiệm rõ giữa các tầng:** tầng đọc file thông báo thao tác I/O thất bại; tầng khởi tạo ứng dụng có thể thêm “không thể khởi tạo dịch vụ”; tầng ngoài quyết định phản hồi hoặc dừng ứng dụng. Nhiều wrapper liên tiếp cùng thông điệp `"failed"` không giúp chẩn đoán tốt hơn.

### Có cần luôn chuyển checked exception thành unchecked?

Không. Nếu người gọi có khả năng phục hồi lỗi đọc cấu hình/tệp, để phương thức khai báo `throws IOException` và truyền `IOException` lên có thể đúng hơn. Bọc sang `IllegalStateException` chỉ nên làm khi tầng hiện tại thật sự muốn coi việc mất cấu hình là **trạng thái hệ thống không thể đáp ứng hợp đồng**.

Ngoại lệ checked cần được xử lý hoặc khai báo đúng luật Java; `throw new IOException(...)` từ một phương thức không khai báo và không tự bắt checked exception sẽ gây lỗi biên dịch (trừ những trường hợp đặc biệt được ngôn ngữ cho phép). `IllegalArgumentException` và `IllegalStateException` là unchecked exception, nên không bắt buộc khai báo `throws`.

## 6. Thông điệp và ngữ cảnh: phần nào đặt ở đâu?

Không phải tất cả ngữ cảnh đều cần nằm trong một chuỗi message. Khi hệ thống lớn dần, nên tách những thông tin có mục đích khác nhau:

| Loại thông tin | Nơi phù hợp | Ví dụ |
| --- | --- | --- |
| Nguyên nhân có thể hành động | Message của ngoại lệ | `"field=age: must be non-negative"` |
| Loại lỗi để chương trình phân nhánh | Kiểu ngoại lệ hoặc mã/thuộc tính có cấu trúc | `InvalidOrderException`, `ErrorCode.INVALID_QUANTITY` |
| Nguyên nhân từ thư viện khác | `Throwable cause` | `NumberFormatException`, `IOException` |
| Định danh vận hành an toàn | Log có cấu trúc hoặc context theo request | Mã tác vụ nội bộ, correlation ID |
| Stack trace | Bản thân `Throwable` và hệ thống log | Nơi tạo/lời gọi khi lỗi được ném |
| Thông báo cho người dùng cuối | Tầng giao diện hoặc API | `"Số lượng phải lớn hơn 0."` |

Ví dụ, ngoại lệ có thông điệp `"Cannot finalize order: status must be PAID"`; tầng API có thể hiển thị một câu tiếng Việt thân thiện; tầng log giữ kiểu ngoại lệ, thời điểm, tên thao tác, mã yêu cầu và chuỗi nguyên nhân. Không nên mặc định dùng nguyên văn `ex.getMessage()` làm nội dung HTTP trả cho khách hàng.

### Khi cần xử lý máy móc, tạo trường dữ liệu có kiểu

Đừng dựa vào:

```java
if (ex.getMessage().contains("quantity")) {
    // Chuyển sang mã lỗi dữ liệu.
}
```

Đây là đoạn minh họa, không phải logic phân loại đáng tin. Message có thể đổi câu chữ hoặc ngôn ngữ, và `getMessage()` có thể là `null`. Nếu bên gọi cần phân biệt lỗi theo cách ổn định, dùng **loại ngoại lệ cụ thể**, **mã lỗi**, hoặc **thuộc tính chuyên biệt**.

Ví dụ một ngoại lệ tùy chỉnh (một lớp cần lưu dưới tên `ValidationException.java`):

```java
public final class ValidationException
        extends IllegalArgumentException {

    private final String field;

    public ValidationException(
            String field, String message, Throwable cause) {
        super(message, cause);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
```

Khi bắt lỗi, code có thể gọi `ex.getField()` thay vì phân tích chuỗi. Tên trường là một **giá trị dữ liệu có cấu trúc**. Nếu cần mã lỗi ổn định cho API, có thể thêm `enum` hoặc hằng mã theo thiết kế của hệ thống. Không nên tạo nhiều lớp ngoại lệ chỉ khác message nếu cách xử lý hoàn toàn giống nhau.

## 7. Chọn đúng loại ngoại lệ trước khi viết message

Thông điệp hay không thể sửa một kiểu ngoại lệ sai. Một số lựa chọn thường gặp:

| Tình huống | Kiểu ngoại lệ cân nhắc | Thông điệp nên tập trung |
| --- | --- | --- |
| Tham số trái điều kiện của phương thức | `IllegalArgumentException` | Tên tham số và điều kiện mong đợi |
| Tham số không được phép `null` | `NullPointerException` hoặc `IllegalArgumentException` tùy hợp đồng API | Tham số bắt buộc |
| Đối tượng ở trạng thái không cho phép thao tác | `IllegalStateException` | Trạng thái hiện tại và yêu cầu |
| Chuỗi không parse được thành số | `NumberFormatException` hoặc wrapper phù hợp | Tên trường và kiểu định dạng mong đợi |
| Không đọc/ghi được tài nguyên I/O | `IOException` hoặc subclass | Thao tác/tài nguyên và nguyên nhân đã xác minh |
| Một điều kiện nghiệp vụ riêng cần caller phân biệt | Ngoại lệ ứng dụng được đặt tên phù hợp | Quy tắc nghiệp vụ, mã/thuộc tính phân loại |
| Không tìm thấy trong phép tìm kiếm mà vắng mặt là bình thường | Có thể là `Optional.empty()` hoặc tập rỗng | Có thể không cần `throw` |

`Objects.requireNonNull(value, "field must not be null")` là một cách tiện lợi để kiểm tra điều kiện không null; thông điệp được dùng nếu phương thức ném `NullPointerException`. Chỉ dùng nó khi điều kiện và kiểu lỗi phù hợp với hợp đồng.

Đừng dùng `Error` cho lỗi nghiệp vụ. `Error` biểu thị các tình huống hệ thống nghiêm trọng theo thiết kế Java và thường không nên bắt để phục hồi như một lỗi nhập liệu.

## 8. Khi nào đưa giá trị lỗi vào thông điệp?

Dữ liệu đầu vào có thể làm quá trình điều tra nhanh hơn, nhưng việc đưa trực tiếp giá trị vào message có rủi ro:

- Token, mật khẩu, cookie, khóa API, mã xác thực một lần hoặc chuỗi kết nối có thông tin đăng nhập có thể bị lộ.
- Dữ liệu cá nhân, số định danh hoặc nội dung khách hàng có thể bị ghi vào log tồn tại lâu và được nhiều hệ thống sao chép.
- Chuỗi không đáng tin có thể chứa ký tự xuống dòng hoặc ký tự điều khiển, làm log khó đọc, gây **log injection** hoặc làm sai cấu trúc bản ghi.
- Dữ liệu rất dài có thể làm log quá tải; một message kiểu `"invalid input=" + entirePayload` có thể vô tình nhân đôi dữ liệu lớn.
- Message có thể xuất hiện qua stack trace, telemetry, hệ thống báo lỗi hoặc trong phản hồi API nếu tầng ngoài trả nguyên văn.

Với một trường nhạy cảm, thay vì:

```java
throw new IllegalArgumentException(
        "invalid credential: " + credential);
```

hãy dùng:

```java
throw new IllegalArgumentException(
        "credential validation failed");
```

Hai câu lệnh chỉ minh họa; `credential` là biến giả định. Nếu cần điều tra nguyên nhân, dùng mã lỗi/ID sự kiện và ghi thông tin **đã được chọn lọc** ở hệ thống quan sát có phân quyền. Không suy ra rằng “không đưa bí mật vào message” đồng nghĩa có thể ghi nguyên văn bí mật vào log ở nơi khác.

Khi định danh an toàn được đưa vào thông điệp, nên giới hạn độ dài và kiểm soát ký tự hoặc dùng logger có tham số/context có cấu trúc. Không nên viết `"user=" + rawRequestBody` để tiết kiệm công xây dựng ngữ cảnh.

## 9. Đừng dùng message thay cho stack trace hoặc cause

Một số mã bắt rồi biến toàn bộ lỗi thành chuỗi:

```java
try {
    loadData();
} catch (java.io.IOException ex) {
    throw new IllegalStateException(ex.toString());
}
```

Đây là đoạn minh họa, giả định `loadData()` có thể ném `IOException`. `ex.toString()` thường gồm tên lớp và thông điệp, nhưng **không bảo toàn đối tượng cause** nếu chỉ truyền vào constructor nhận `String`.

Cách an toàn hơn:

```java
try {
    loadData();
} catch (java.io.IOException cause) {
    throw new IllegalStateException(
            "Data loading failed", cause);
}
```

Nếu logger nhận một đối tượng `Throwable`, truyền đối tượng lỗi như tham số chuyên dụng theo API logger đang dùng giúp ghi được stack trace và cause. Không nên chỉ in `ex.getMessage()` rồi coi như đã lưu đủ thông tin điều tra.

### `throw ex;` hay `throw new ...(..., ex);`?

- `throw ex;`: ném lại **cùng đối tượng** đã bắt, giữ loại và cause hiện có. Phù hợp khi tầng hiện tại không cần đổi hợp đồng, nhưng có thể đã làm một việc như dọn dẹp.
- `throw new SomeException("context", ex);`: tạo **ngoại lệ mới** với thông điệp bổ sung và cause trỏ đến lỗi gốc; phù hợp khi cần biểu diễn lỗi ở cấp trừu tượng khác.
- `throw new SomeException("context");` sau khi đã bắt một lỗi khác: có nguy cơ đánh mất mối liên hệ nguyên nhân nếu không có cơ chế lưu khác.

Một số đoạn mã dùng `fillInStackTrace()` hoặc tạo ngoại lệ mới chỉ để điều chỉnh vị trí hiển thị stack trace; đừng làm điều này để “che” nguồn lỗi nếu không có nhu cầu đặc biệt và hiểu đầy đủ hệ quả. Stack trace thường phản ánh thời điểm đối tượng ngoại lệ được tạo/khởi tạo stack trace; chuỗi `Caused by` mới cho thấy các nguyên nhân nằm dưới wrapper.

## 10. Logging và trách nhiệm ở từng tầng

Một lỗi có thể đi qua thư viện, service, controller, job scheduler và lớp giao diện. Nếu **mỗi tầng đều in cả stack trace rồi ném tiếp**, hệ thống có thể ghi một sự cố thành nhiều bản log gần như trùng nhau.

Một mô hình thường hợp lý:

1. **Nơi phát hiện:** ném đúng loại lỗi với message nói rõ điều kiện.
2. **Tầng nghiệp vụ:** chỉ bọc lỗi nếu thêm được ngữ cảnh mới hoặc thay đổi hợp đồng; giữ cause. Không bắt chỉ để ghi log y hệt rồi ném.
3. **Tầng giao tiếp hoặc biên tác vụ:** chuyển lỗi thành kết quả/HTTP/status thất bại thích hợp; đây có thể là nơi log lỗi một cách tập trung.
4. **Hệ thống quan sát:** bổ sung mã tương quan, thời gian, tên thao tác, độ nghiêm trọng và các trường được phép ghi.

Trạng thái HTTP, thông báo người dùng và chuỗi message không nhất thiết giống nhau. Chẳng hạn, lỗi thiếu quyền nội bộ cần phản hồi công khai hạn chế thông tin, còn log vận hành được kiểm soát có thể chứa thông tin nguyên nhân sâu hơn.

Tại ranh giới API, đừng hiển thị mọi exception message cho client. Ngoài lộ thông tin, message còn có thể là nội dung kỹ thuật, chưa được bản địa hóa, hoặc thay đổi khi đổi phiên bản thư viện.

## 11. Một phương thức checked exception bổ sung ngữ cảnh ra sao?

Ví dụ sau **chỉ là một phương thức minh họa**, không phải chương trình độc lập. Nó đọc tệp và thêm tên logic của cấu hình trong message nếu gặp lỗi, đồng thời giữ `cause`:

```java
static String readRequiredConfig(
        java.nio.file.Path path, String logicalName)
        throws java.io.IOException {
    try {
        return java.nio.file.Files.readString(path);
    } catch (java.io.IOException cause) {
        throw new java.io.IOException(
                "Cannot read required configuration: " + logicalName,
                cause);
    }
}
```

Ý nghĩa:

- `Files.readString` có thể ném nhiều loại `IOException`; thông điệp wrapper chỉ nói **không đọc được cấu hình**, không tự khẳng định tệp không tồn tại.
- `logicalName` được giả định là **tên nội bộ an toàn**, không phải đường dẫn hay dữ liệu người dùng có chứa bí mật. Nếu không có định danh an toàn, có thể bỏ nó khỏi message.
- Thông tin sâu như `NoSuchFileException` hoặc nguyên nhân I/O khác vẫn có thể có trong `getCause()`.
- Vì vẫn ném `IOException` checked, chữ ký `throws IOException` phù hợp; caller phải tiếp tục khai báo hoặc bắt khi dùng phương thức.
- Nếu API không cần đổi message mà vẫn hữu ích, có thể để ngoại lệ gốc truyền lên và log bối cảnh ở ranh giới thích hợp.

**Không nên thay kiểu ngoại lệ gốc bằng một wrapper quá rộng chỉ để “có thông điệp đẹp”**. Khi caller cần phân biệt `NoSuchFileException` với lỗi I/O khác, bọc mọi thứ trong `IOException` mới có thể làm mất khả năng `catch (NoSuchFileException)` trực tiếp, mặc dù cause còn nguyên. Hãy cân nhắc điều đó trước khi thay đổi hợp đồng kiểu của API.

## 12. Các lỗi thường gặp khi viết `throw`

### A. Thông điệp quá ngắn

```java
throw new IllegalStateException("Invalid state");
```

Thường không đủ bối cảnh. Tốt hơn là nêu **trạng thái hiện tại và trạng thái yêu cầu** nếu cả hai đã biết và được phép hiển thị, chẳng hạn `"cannot submit order: expected DRAFT status"`.

### B. Thông điệp chứa dữ liệu nhạy cảm

```java
throw new IllegalArgumentException(
        "bad password: " + password);
```

Đây là **ví dụ không nên làm**. Không cho mật khẩu xuất hiện trong exception message, dù đối tượng chỉ được ném ở tầng nội bộ; nhiều framework tự động ghi stack trace khi không bắt được lỗi.

### C. Bọc nhưng quên `cause`

```java
catch (java.io.IOException ex) {
    throw new IllegalStateException("Startup failed");
}
```

Nếu chính sách yêu cầu đổi kiểu, hãy dùng constructor truyền `ex` làm nguyên nhân; nếu không cần đổi, có thể để `IOException` đi lên.

### D. Lấy message làm mã lỗi

```java
if ("quantity must be positive".equals(ex.getMessage())) {
    // ...
}
```

Đây là **mã không nên dùng để phân nhánh nghiệp vụ**. Một lần sửa câu chữ có thể thay đổi hành vi. Dùng kiểu ngoại lệ, enum lỗi hoặc thuộc tính cụ thể.

### E. Cứ nhận lỗi là thêm wrapper

Viết `throw new RuntimeException("Failed at A", ex)` trong A, rồi `"Failed at B"` ở B, rồi `"Failed at C"` ở C khiến stack trace dài mà ít thêm nghĩa. Hãy chỉ bọc tại tầng có **ngữ cảnh mới, thật và hữu ích**.

### F. Thông điệp khẳng định nguyên nhân chưa được kiểm chứng

Nếu API ném `IOException`, đừng lập tức viết `"network is offline"` nếu chưa biết chắc. Dùng `"remote operation failed"` rồi để `cause` cung cấp chi tiết kỹ thuật, hoặc phân loại thêm bằng ngoại lệ cụ thể.

### G. Bắt rồi chuyển lỗi thành thành công giả

```java
try {
    return parseRequiredValue();
} catch (RuntimeException ex) {
    return 0;
}
```

Đây là **đoạn minh họa chống mẫu**, giả định phương thức trả về `int`. Nếu `0` hợp lệ, caller không phân biệt thành công với thất bại. Khi hợp đồng không cho phép giá trị dự phòng, phải truyền lỗi có ý nghĩa; nếu `0` là giá trị mặc định hợp lệ theo chính sách, hãy ghi rõ hợp đồng và giới hạn loại lỗi có thể được thay thế.

### H. Thông điệp gây hiểu sai vì cùng lúc báo nhiều tầng

Ví dụ ở tầng parse, viết `"Database server is down"` chỉ vì hàm được gọi trong quy trình lưu dữ liệu là sai nếu lỗi thực sự do nhập sai số. Thông điệp cần bám vào **điều đã được xác minh ở nơi ném**.

## 13. Quy trình thiết kế một ngoại lệ dễ bảo trì

Khi định viết `throw`, có thể áp dụng thứ tự sau:

1. **Định nghĩa điều kiện thất bại.** Đây là lỗi bắt buộc dừng, hay một kết quả vắng mặt hợp lệ? Không ném ngoại lệ chỉ vì một phép tìm kiếm thông thường không trả kết quả.
2. **Chọn kiểu ngoại lệ phù hợp.** Ví dụ `IllegalArgumentException` cho tiền điều kiện tham số, `IllegalStateException` cho trạng thái đối tượng, `IOException` cho thao tác I/O không thành công.
3. **Viết message có thể hành động.** Gọi tên trường hoặc thao tác và điều kiện bị vi phạm. Tránh `"error"`, `"failed"` đơn độc.
4. **Thêm ngữ cảnh đúng tầng.** Nếu cấp gọi có một mã tác vụ an toàn giúp nhận biết lỗi, thêm tại đó hoặc vào log có cấu trúc. Không đoán thông tin mà tầng đó không biết.
5. **Giữ nguyên nhân gốc nếu đổi ngoại lệ.** Dùng constructor nhận `cause`. Kiểm tra hợp đồng của kiểu mới: việc đổi loại không vô tình che đi một nhánh `catch` cần thiết.
6. **Rà soát dữ liệu nhạy cảm.** Message, `cause`, stack trace và log có thể ra khỏi tiến trình. Không cho secret hoặc payload thô vào đó.
7. **Đặt handler ở nơi có thể xử lý.** Bắt tại nơi có thể phục hồi hoặc chuyển thành phản hồi phù hợp; đừng nuốt lỗi trong tầng thấp.
8. **Kiểm thử nhánh thất bại.** Kiểm tra loại ngoại lệ, cause và dữ liệu có cấu trúc; tránh kiểm tra toàn bộ câu message nếu câu chữ không là một phần hợp đồng công khai.

## 14. Bài tập tình huống

**Tình huống 1:** Phương thức nhận tham số `retryCount = -2`, hợp đồng quy định không âm. Nên ném gì?

Có thể dùng:

```java
throw new IllegalArgumentException(
        "retryCount must be non-negative");
```

Lỗi rõ tên tham số và điều kiện. Không cần in chính giá trị nếu nó không giúp ích thêm hoặc có chính sách hạn chế ghi đầu vào.

**Tình huống 2:** Tầng đọc tệp ném `IOException`. Tầng khởi tạo ứng dụng biết rằng tệp là cấu hình bắt buộc nhưng không biết nguyên nhân kỹ thuật. Nên báo ra sao?

Có thể bọc bằng `new IllegalStateException("Service startup failed: required configuration could not be read", cause)` nếu tầng đó quyết định không thể khởi tạo. Giữ `cause`; không khẳng định “tệp không tồn tại” trừ khi có bằng chứng.

**Tình huống 3:** Caller cần biết lỗi do `quantity` hay `price` để trả về lỗi chính xác của từng trường. Có nên kiểm tra `ex.getMessage().contains("quantity")`?

Không. Nếu caller cần phân loại ổn định, hãy cung cấp thuộc tính `field` hoặc một enum/mã lỗi trên ngoại lệ chuyên biệt. Message dành cho người đọc và chẩn đoán, không phải giao thức máy móc dựa trên từ khóa dễ thay đổi.

**Tình huống 4:** Lỗi từ thư viện đã có kiểu và message rất rõ, tầng giữa không có thêm ngữ cảnh. Có nên bọc lỗi một lần nữa cho “đầy đủ” không?

Thường không cần. Cho phép ngoại lệ lan truyền giúp giữ nguyên kiểu, giảm số wrapper và để tầng có trách nhiệm xử lý.

**Tình huống 5:** Exception message ghi nguyên văn yêu cầu có chứa mã xác thực. Hệ thống chỉ in lỗi ở môi trường phát triển. Có an toàn không?

Không nên. Môi trường, logger và chính sách lưu trữ có thể thay đổi; bí mật có thể bị gửi ra telemetry hoặc báo cáo lỗi. Hãy chọn trường ngữ cảnh an toàn từ đầu và giới hạn dữ liệu nhạy cảm trong cả message lẫn log.

**Điểm cần ghi nhớ:** `throw` cho biết **thao tác đã thất bại**; **kiểu ngoại lệ** cho biết lỗi thuộc loại nào; **message** giải thích điều kiện và thao tác thất bại; **cause** giữ dấu nguyên nhân sâu hơn; **ngữ cảnh có cấu trúc và an toàn** giúp truy vết đúng sự kiện. Khi các phần này được thiết kế nhất quán, lỗi dễ xử lý, dễ kiểm thử và dễ điều tra mà không phải để lộ dữ liệu không cần thiết.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="throw-statement-005">throw null và NullPointerException</a>

<details>
<summary>Xem chi tiết</summary>

# throw null và NullPointerException trong Java

> Java Core – Exception · Nhóm: Lệnh throw · Ví dụ chạy trên Java 17 trở lên.

## 1. Hiểu đúng trong một câu

`throw null;` **biên dịch được**, nhưng khi câu lệnh này thực sự chạy, Java **tạo và ném một đối tượng `NullPointerException`**. Giá trị `null` không phải là đối tượng ngoại lệ; không tồn tại việc một khối `catch` nhận “exception có giá trị null”.

Đây là quy tắc của **Java Language Specification (JLS), mục 14.18**, áp dụng cho mọi giá trị tham chiếu null mà một biểu thức `throw` trả về bình thường, không riêng từ khóa `null` viết trực tiếp trong mã.

Sau bài học, bạn sẽ phân biệt được:

1. `throw null` với `throw new NullPointerException(...)`.
2. `throw variable` khi biến chứa null và khi biến chứa đối tượng `Throwable` thực.
3. `throw factory()` khi factory trả về null so với khi factory tự ném exception trước khi trả về.
4. Ràng buộc kiểu tĩnh tại thời điểm biên dịch so với kiểu ngoại lệ được ném tại runtime.
5. Cách bắt, chẩn đoán và tránh những lỗi “throw null” phát sinh từ code thực tế.

## 2. Quy tắc chuẩn cho câu lệnh throw

Java dùng cú pháp:

```java
throw expression;
```

Theo [JLS 17 – The throw Statement, mục 14.18](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.18), biểu thức sau `throw` cần có kiểu có thể gán cho `Throwable` hoặc là giá trị null literal. Trong lúc thực thi, Java đánh giá biểu thức trước, rồi xử lý theo một trong ba tình huống:

| Kết quả của `expression` | Hành vi của `throw` | Ngoại lệ quan sát được |
| --- | --- | --- |
| Đánh giá xong bình thường, nhận một đối tượng khác null | Ném **chính đối tượng đó** | Kiểu runtime của đối tượng |
| Đánh giá xong bình thường, nhận tham chiếu `null` | **Tạo và ném** một đối tượng `NullPointerException` | `NullPointerException` |
| Bản thân biểu thức ném exception trước khi có kết quả | Lệnh `throw` kết thúc bất thường bởi exception đó; không có bước ném giá trị cuối | Kiểu exception do biểu thức gây ra |

Có thể hình dung:

```text
              throw expression;
                       |
                 Tính expression
                  /          \
         Thành công           Tự ném exception E
             |                      |
      Kết quả là gì?                v
        /          \            Lan truyền E
   Khác null       null
      |             |
      v             v
 Ném chính      JVM tạo NPE
 đối tượng      rồi ném NPE
      \             /
       \           /
          Tìm catch
          hoặc lan lên caller
```

JLS định nghĩa rõ hai điều dễ nhầm: `throw` nhận biểu thức **không bắt buộc là lệnh `new`**, và giá trị cần ném **không được trở thành một Throwable có giá trị null**. Nếu kết quả là null, một NPE mới xuất hiện thay thế.

### Câu lệnh throw kết thúc bất thường

Trong cấu trúc:

```java
try {
    throw null;
    // Không được viết thêm câu lệnh có thể thực thi ở đây:
    // javac sẽ đánh dấu unreachable statement.
} catch (NullPointerException ex) {
    System.out.println("caught NPE");
}
```

Những dòng sau `throw` trong cùng một nhánh thông thường không thể chạy. Nếu đặt một câu lệnh thực thi vô điều kiện sau `throw null;` trong cùng khối, compiler sẽ báo *unreachable statement*. Các dòng chú thích ở ví dụ không phải câu lệnh thực thi, nên đoạn minh họa có thể dùng bên trong một phương thức.

Nếu nhánh `catch` phù hợp, chương trình có thể tiếp tục **sau cấu trúc try/catch**, không quay về chỗ throw.

## 3. Vì sao throw null không gây lỗi biên dịch?

Java có **null type** cho literal `null`. JLS quy định đây là giá trị được phép sử dụng trực tiếp trong biểu thức của câu lệnh `throw`. Vì vậy:

```java
throw null;
```

là cú pháp hợp lệ. Đây là ví dụ minh họa đặt trong phương thức thích hợp; riêng tệp nguồn chỉ chứa dòng này ngoài lớp Java thì không phải chương trình độc lập.

Các ví dụ gần nhau có kết quả khác biệt:

| Mã nguồn (đặt đúng ngữ cảnh) | Hợp lệ ở compile time? | Điều xảy ra khi chạy |
| --- | --- | --- |
| `throw null;` | Có | NPE do runtime tạo |
| `throw (RuntimeException) null;` | Có | NPE do runtime tạo; ép kiểu null vẫn là null |
| `RuntimeException ex = null; throw ex;` | Có | NPE do runtime tạo |
| `throw new NullPointerException("bad");` | Có | Ném NPE do lập trình viên tạo, message `bad` |
| `throw new IllegalStateException("failed");` | Có | Ném `IllegalStateException`, không phải NPE |
| `throw 42;` | Không | `int` không phù hợp với `Throwable` |
| `throw "failed";` | Không | `String` không phải subtype của `Throwable` |
| `throw new Object();` | Không | `Object` không gán được cho `Throwable` |
| `throw (Object) null;` | Không | Kiểu tĩnh `Object` không phù hợp dù giá trị là null |
| `throw;` | Không | Java yêu cầu biểu thức sau `throw` |

Lỗi của các dòng cố ý không hợp lệ thuộc **biên dịch**; NPE của `throw null` thuộc **runtime**. Không nên trộn hai giai đoạn khi giải thích.

## 4. Ba nguồn NullPointerException hay bị gộp

Xét ba đoạn minh họa, mỗi đoạn được viết trong một phương thức Java thích hợp.

**Trường hợp A — ném null:**

```java
throw null;
```

Biểu thức `null` hoàn thành bình thường, nhưng giá trị cần ném là null; Java tạo NPE.

**Trường hợp B — chủ động tạo một đối tượng NPE:**

```java
throw new NullPointerException("customerId must not be null");
```

Đối tượng NPE đã được tạo bằng `new` với message có ý nghĩa. `throw` ném chính đối tượng đó.

**Trường hợp C — sử dụng tham chiếu null sai cách:**

```java
String customerName = null;
int length = customerName.length();
```

Không có lệnh `throw` tường minh trong code, nhưng JVM ném NPE khi cố gọi phương thức qua tham chiếu null.

Các đoạn trên đều có thể dẫn đến **cùng kiểu NPE**, song khác nguyên nhân và vị trí trong stack trace. Đừng sửa tất cả NPE bằng cách thêm `catch (NullPointerException)`: cần tìm nguồn vi phạm hợp đồng null.

### Kiểu và phân loại NPE

```text
java.lang.Throwable
    └── java.lang.Exception
        └── java.lang.RuntimeException
            └── java.lang.NullPointerException
```

NPE là **unchecked exception** vì kế thừa `RuntimeException`. Do đó phương thức có thể ném NPE mà không bắt buộc viết `throws NullPointerException`. Điều này không có nghĩa lỗi không cần được xem xét; NPE thường báo hiệu trạng thái hoặc tham số không đáp ứng giả định của chương trình.

## 5. Ví dụ hoàn chỉnh Java 17: literal, biến, factory và lỗi chủ động

Lưu mã sau thành đúng tên tệp **`ThrowNullDemo.java`**. Đây là một chương trình chạy độc lập, không cần thư viện ngoài. Mỗi nhánh được bao bởi cùng một hàm kiểm thử để quan sát handler và cleanup.

```java
public class ThrowNullDemo {
    private static RuntimeException returnsNull() {
        System.out.println("factory returned null");
        return null;
    }

    private static RuntimeException failsFirst() {
        System.out.println("factory started");
        throw new IllegalStateException("factory failed");
    }

    private static void test(String label, Runnable task) {
        System.out.println("== " + label + " ==");
        try {
            task.run();
            System.out.println("unreachable");
        } catch (NullPointerException ex) {
            System.out.println("caught=NullPointerException");
            if (label.equals("explicit")) {
                System.out.println("message=" + ex.getMessage());
            }
        } catch (IllegalStateException ex) {
            System.out.println(
                    "caught=IllegalStateException: " + ex.getMessage());
        } finally {
            System.out.println("finally");
        }
        System.out.println("after");
    }

    public static void main(String[] args) {
        test("literal", () -> { throw null; });

        test("variable", () -> {
            RuntimeException ex = null;
            throw ex;
        });

        test("returned-null", () -> { throw returnsNull(); });

        test("factory-throws", () -> { throw failsFirst(); });

        test("explicit", () -> {
            throw new NullPointerException("manual message");
        });
    }
}
```

Dùng JDK 17 trở lên, trong thư mục chứa tệp:

```bash
javac -encoding UTF-8 ThrowNullDemo.java
java ThrowNullDemo
```

Stdout **chính xác** của chương trình:

```text
== literal ==
caught=NullPointerException
finally
after
== variable ==
caught=NullPointerException
finally
after
== returned-null ==
factory returned null
caught=NullPointerException
finally
after
== factory-throws ==
factory started
caught=IllegalStateException: factory failed
finally
after
== explicit ==
caught=NullPointerException
message=manual message
finally
after
```

Kết quả đã được đối chiếu khi biên dịch và chạy trên OpenJDK. Mã cố ý **không in `ex.getMessage()` đối với NPE do JVM tạo**, để stdout không phụ thuộc chi tiết chẩn đoán giữa các JVM. Riêng nhánh `explicit` in message do chính mã nguồn truyền vào constructor.

### Phân tích năm nhánh

1. **`literal`:** Thực thi `throw null`, nhận NPE do JVM tạo. Handler `catch (NullPointerException ex)` bắt đối tượng này.
2. **`variable`:** Biến có kiểu tĩnh `RuntimeException` nhưng giá trị thực là null. Kiểu tĩnh hợp lệ không biến null thành instance của RuntimeException; khi ném, NPE xuất hiện.
3. **`returned-null`:** `returnsNull()` in `factory returned null` rồi trả null bình thường. Chỉ **sau đó** lệnh `throw` mới tạo NPE. Side effect in dòng trước đã xảy ra.
4. **`factory-throws`:** `failsFirst()` in `factory started` rồi tự ném `IllegalStateException` trước khi trả về. Không có kết quả null để lệnh throw xử lý; exception gốc đi thẳng đến nhánh catch tương ứng.
5. **`explicit`:** `new NullPointerException("manual message")` tạo đối tượng có message xác định; `throw` ném đối tượng đó.

Ở mọi nhánh, `task.run()` kết thúc bất thường nên dòng `unreachable` không bao giờ được in. `finally` chạy, rồi dòng `after` chạy bởi handler đã xử lý xong và hoàn thành bình thường. Không có nhánh nào thực thi nốt phần thân của lambda sau khi lỗi đã xảy ra.

## 6. Đọc đúng biểu thức phía sau throw

Một lỗi phân tích phổ biến là thấy:

```java
throw createException();
```

rồi kết luận chương trình luôn ném đúng kiểu mà `createException()` khai báo là trả về. Thực tế phải tách hai giai đoạn:

- **Giai đoạn đánh giá:** Gọi `createException()`. Nếu nó ném một ngoại lệ trước khi trả về, ngoại lệ đó lan đi ngay.
- **Giai đoạn ném:** Chỉ xảy ra nếu lời gọi trả về bình thường. Kết quả không null được ném trực tiếp; kết quả null gây NPE.

Xem một factory minh họa (phương thức đặt trong một lớp Java; chưa là tệp độc lập):

```java
static RuntimeException createFailure(boolean hasReason) {
    return hasReason
            ? new IllegalArgumentException("bad input")
            : null;
}
```

- `throw createFailure(true);` ném `IllegalArgumentException`.
- `throw createFailure(false);` ném NPE.
- Nếu chính quá trình tính điều kiện hoặc tạo đối tượng ném lỗi, lỗi đó là lý do kết thúc bất thường tại thời điểm ấy.

Điều này quan trọng khi một factory, hàm chuyển đổi, bộ dựng exception hoặc callback vô tình **trả về null như tín hiệu “không có lỗi”**. Dùng kết quả này trong câu lệnh `throw` khiến “không có lỗi” bị hiểu thành NPE bất ngờ.

## 7. Quan hệ giữa throw null, catch và finally

NPE tạo từ `throw null` vẫn là một instance của lớp `NullPointerException`. Vì thế:

```java
try {
    throw null;
} catch (NullPointerException ex) {
    System.out.println(ex != null);
}
```

Đoạn trong một phương thức hợp lệ sẽ in `true`. Biến `ex` tham chiếu tới NPE mà runtime tạo. Java không cho phép một biến handler nhận “ngoại lệ null” trong trường hợp này.

Nếu muốn xử lý NPE cụ thể rồi mới đến loại RuntimeException khác, đặt handler cho kiểu con trước kiểu cha:

```java
try {
    throw null;
} catch (NullPointerException ex) {
    System.out.println("NPE");
} catch (RuntimeException ex) {
    System.out.println("other runtime failure");
}
```

Đảo hai nhánh `catch` gây lỗi biên dịch vì nhánh NPE phía sau không thể tới được. `catch (Exception)` cũng có thể bắt NPE, nhưng bắt rộng theo thói quen dễ che các lỗi logic.

Với `finally`:

```java
try {
    throw null;
} catch (NullPointerException ex) {
    System.out.println("caught");
} finally {
    System.out.println("cleanup");
}
System.out.println("continue");
```

Nếu nằm trong một phương thức, stdout là:

```text
caught
cleanup
continue
```

`finally` là nhánh dọn dẹp khi rời cấu trúc, **không** khiến phép đánh giá bị lỗi được chạy lại. Nếu `finally` lại ném exception khác, kết quả điều khiển có thể bị thay thế và exception ban đầu bị che mất. Tránh viết `return` hay `throw` mới trong `finally` khi không có lý do rõ ràng.

## 8. Phân biệt loại ngoại lệ runtime và kiểu tĩnh tại compile time

Đây là một điểm dễ gây bất ngờ: `throw null;` có biểu thức thuộc **null type**, được JLS cho phép trực tiếp. Nhưng khi ném **một biến có kiểu checked exception**, compiler xét kiểu tĩnh, dù giá trị thực của biến là null.

Ví dụ Java 17 độc lập, lưu thành `TypedNullExample.java`:

```java
import java.io.IOException;

public class TypedNullExample {
    static void fail() throws IOException {
        IOException ex = null;
        throw ex;
    }

    public static void main(String[] args) {
        try {
            fail();
        } catch (IOException ex) {
            System.out.println("caught IOException");
        } catch (NullPointerException ex) {
            System.out.println("caught NPE");
        }
    }
}
```

Chạy:

```bash
javac TypedNullExample.java
java TypedNullExample
```

Output:

```text
caught NPE
```

**Giải thích:** `ex` có kiểu tĩnh `IOException` (checked), vì vậy phương thức `fail()` khai báo `throws IOException`. Tuy nhiên, giá trị runtime của `ex` là null nên câu lệnh ném dẫn tới NPE unchecked. Không có `IOException` thực sự nào được tạo hoặc ném trong lần chạy này.

Một biến kiểu `Exception` hoặc `Throwable` càng cần xem kỹ điều kiện bắt/khai báo checked exception. Đừng bỏ qua ràng buộc compiler chỉ vì test cho thấy một nhánh hiện tại trả về null. Ngược lại, viết `throws NullPointerException` là tùy chọn về mặt ngôn ngữ do NPE là unchecked exception.

## 9. Helpful NullPointerExceptions và thông điệp lỗi

Các JDK hiện đại có khả năng đưa ra thông điệp NPE hữu ích dựa trên thao tác bytecode gây lỗi. OpenJDK JEP 358 bổ sung cơ chế này từ JDK 14; trên HotSpot, tùy chọn `ShowCodeDetailsInExceptionMessages` được bật mặc định từ JDK 15.

Trong một số tình huống, message có thể cho biết đang cố gọi phương thức, đọc trường hoặc **ném một tham chiếu null**. Nhưng phải phân biệt:

| Phần nào | Tính ổn định |
| --- | --- |
| Giá trị sau `throw` là null → Java ném NPE | Quy tắc ngôn ngữ, được JLS xác định |
| `getMessage()` của NPE do JVM tạo | Có thể phụ thuộc JVM, bytecode và cấu hình |
| `new NullPointerException("manual")` → message `manual` | Thông điệp được constructor nhận từ code ứng dụng |
| Stack trace, số dòng, tên biến biểu thị null | Phụ thuộc cách biên dịch và thông tin runtime |

Theo tài liệu API Java 17, `NullPointerException.getMessage()` có thể trả về message do constructor cung cấp, thông điệp tùy triển khai hoặc `null`. Với một NPE do người viết mã chủ động tạo không có message, không mặc định suy ra hệ thống sẽ bổ sung thông điệp phân tích biểu thức.

Nếu dùng JVM HotSpot có hỗ trợ, có thể bật hoặc tắt phần thông tin này để chẩn đoán:

```bash
java -XX:+ShowCodeDetailsInExceptionMessages ThrowNullDemo
java -XX:-ShowCodeDetailsInExceptionMessages ThrowNullDemo
```

Trong ví dụ chính, stdout **không đổi** vì mã chỉ in tên kiểu của NPE runtime, không in message của các NPE đó. Đây là tùy chọn VM cụ thể; không coi mọi JVM đều hỗ trợ nguyên xi cờ.

Với unit test, ưu tiên khẳng định **kiểu exception** và trạng thái chương trình. Chỉ khẳng định nguyên văn message khi nó do API của chính bạn hoặc hợp đồng thư viện bảo đảm.

## 10. Viết mã tốt hơn throw null

### Tạo ngoại lệ có ý nghĩa

Đối với tham số không hợp lệ:

```java
if (age < 0) {
    throw new IllegalArgumentException("age must be non-negative");
}
```

Đối với tham số bắt buộc khác null:

```java
if (customerId == null) {
    throw new NullPointerException("customerId must not be null");
}
```

Hai đoạn trên chỉ là ví dụ bên trong phương thức, giả định biến tương ứng đã khai báo. Chọn loại exception theo **hợp đồng của API**, thay vì sử dụng NPE cho mọi lỗi nghiệp vụ.

### Dùng Objects.requireNonNull khi cần kiểm tra null

Java chuẩn có `Objects.requireNonNull`; với overload nhận `String` message, nó trả về tham chiếu đầu vào nếu khác null, hoặc ném NPE với message chỉ định nếu đầu vào null.

Tệp Java 17 độc lập `RequireNonNullDemo.java`:

```java
import java.util.Objects;

public class RequireNonNullDemo {
    static void process(String id) {
        String checked = Objects.requireNonNull(
                id, "id must not be null");
        System.out.println(checked);
    }

    public static void main(String[] args) {
        try {
            process(null);
        } catch (NullPointerException ex) {
            System.out.println(ex.getMessage());
        }
    }
}
```

Chạy:

```bash
javac RequireNonNullDemo.java
java RequireNonNullDemo
```

Stdout:

```text
id must not be null
```

Đây là kiểm tra **ở ranh giới nhận dữ liệu**. Nếu null là trạng thái hợp lệ của API (ví dụ truy vấn không có kết quả), nên thiết kế cách biểu đạt trạng thái vắng mặt rõ ràng như `Optional<T>` khi phù hợp, thay vì cố dùng một exception để thay thế điều kiện bình thường.

### Không viết factory ngoại lệ trả về null

Thiết kế nguy hiểm:

```java
static RuntimeException buildFailure(String reason) {
    if (reason == null) {
        return null;
    }
    return new IllegalStateException(reason);
}
```

Nếu caller viết `throw buildFailure(null);`, chương trình ném NPE thay vì loại ngoại lệ nghiệp vụ định dùng.

Thiết kế rõ ràng hơn:

```java
static IllegalStateException buildFailure(String reason) {
    String message = reason == null
            ? "operation failed"
            : reason;
    return new IllegalStateException(message);
}
```

Hai đoạn factory chỉ minh họa một phương thức đặt trong class, **chưa phải tệp Java độc lập**. Chúng cần được điều chỉnh theo quy tắc nghiệp vụ thật: khi không có lỗi, đừng gọi `throw`; khi có lỗi, trả về hoặc tạo một Throwable không null, giữ `cause` khi bọc lỗi gốc.

## 11. Những lỗi hiểu sai thường gặp

1. **“throw null phải lỗi biên dịch.”** Sai: null literal được JLS cho phép; runtime ném NPE.
2. **“Có thể ném một đối tượng exception có giá trị null.”** Sai: null không phải một object; Java tạo NPE.
3. **“catch nhận null.”** Sai: handler nhận đối tượng NPE thực sự.
4. **“Biến có kiểu RuntimeException chắc chắn chứa một RuntimeException.”** Sai: biến tham chiếu có thể chứa null.
5. **“factory đã khai báo RuntimeException, vậy throw factory() chắc chắn ném RuntimeException.”** Sai: factory có thể trả null, hoặc tự ném một exception khác trước khi trả về.
6. **“throw new NullPointerException và throw null hoàn toàn giống nhau.”** Sai: cả hai có thể cùng kiểu NPE nhưng khác nguồn gốc object và cách tạo message.
7. **“Mọi NPE đều có message hữu ích.”** Sai: `getMessage()` có thể null hoặc thay đổi theo triển khai.
8. **“NPE là unchecked nên cứ bỏ qua.”** Sai: compiler không bắt buộc khai báo, nhưng lỗi vẫn gián đoạn luồng thực thi.
9. **“Biết biến checked exception là null nên không cần throws.”** Sai: kiểm tra checked exception phụ thuộc kiểu tĩnh của biểu thức.
10. **“Catch NPE là đã sửa bug null.”** Sai: thường cần kiểm tra hợp đồng, khởi tạo đúng và truy nguyên nơi tạo null.
11. **“throw null sẽ tự bảo toàn cause của lỗi trước.”** Sai: chưa có Throwable cụ thể để dùng làm cause; nếu cần bảo toàn, hãy tạo ngoại lệ mới với constructor nhận cause.
12. **“finally chạy sẽ tiếp tục nốt lệnh throw.”** Sai: cleanup chỉ diễn ra khi chuyển điều khiển, không làm phép đánh giá ban đầu chạy lại.

## 12. Bài tự kiểm tra

**Câu 1.** Kết quả của `throw null;` tại runtime là loại exception nào? Vì sao nó hợp lệ lúc biên dịch?

*Đáp án:* NPE. Java cho phép null literal sau `throw`, và nếu giá trị được ném là null, runtime tạo một NPE.

**Câu 2.** `RuntimeException ex = null; throw ex;` có ném chính đối tượng RuntimeException không?

*Đáp án:* Không có đối tượng RuntimeException nào trong biến. Giá trị null dẫn tới NPE do runtime tạo.

**Câu 3.** Với `throw makeException();`, nếu `makeException()` ném `IllegalStateException` trước khi trả về, kết quả là gì?

*Đáp án:* `IllegalStateException` lan truyền ngay. Lệnh throw không có giá trị trả về để xử lý thành NPE.

**Câu 4.** Vì sao method `fail() throws IOException` có thể chạy ra NPE khi thực thi `IOException x = null; throw x;`?

*Đáp án:* Kiểu tĩnh checked `IOException` quyết định yêu cầu khai báo, còn giá trị runtime null khiến Java tạo NPE.

**Câu 5.** Có nên viết test so sánh nguyên văn message do JVM tạo cho `throw null` không?

*Đáp án:* Không. Kiểu ngoại lệ ổn định theo JLS, còn message chẩn đoán có thể phụ thuộc triển khai.

**Câu 6.** Khi một factory trả null nhưng caller đang viết `throw factory();`, sửa ở đâu?

*Đáp án:* Xem lại hợp đồng: nếu không có lỗi thì tránh ném, nếu có lỗi thì phải tạo Throwable khác null với loại, message và cause phù hợp.

## 13. Tài liệu chuẩn

- [JLS 17, mục 14.18 – The throw Statement](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.18): quy tắc đánh giá biểu thức và tạo NPE khi giá trị sau throw là null.
- [JLS 17, mục 11.2 – Compile-Time Checking of Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.2): cách compiler phân tích và yêu cầu khai báo checked exceptions.
- [Java 17 API – NullPointerException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/NullPointerException.html): phân cấp và thông điệp NPE, bao gồm trường hợp “throwing null”.
- [Java 17 API – Objects](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Objects.html): các overload `requireNonNull`.
- [OpenJDK JEP 358 – Helpful NullPointerExceptions](https://openjdk.org/jeps/358): cơ chế bổ sung chi tiết chẩn đoán NPE.

**Ghi nhớ:** Cần một **đối tượng Throwable thực** để ném một ngoại lệ có ý nghĩa. Nếu biểu thức sau `throw` chỉ trả về null, Java **ném NPE mới**. Hãy đặt ràng buộc null rõ ràng, đọc đúng kiểu tĩnh và bảo toàn nguyên nhân lỗi khi thiết kế exception.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="throw-statement-006">Ném lại cùng đối tượng ngoại lệ bằng throw e</a>

<details>
<summary>Xem chi tiết</summary>

# Ném lại cùng đối tượng ngoại lệ bằng `throw e` trong Java

Trong Java, khi một ngoại lệ đã được bắt bằng `catch`, lập trình viên có thể **ném lại chính đối tượng ngoại lệ đó** bằng lệnh `throw e;`. Cách làm này gọi là **rethrow** (ném lại). Nó hữu ích khi tầng hiện tại cần thực hiện một công việc giới hạn, chẳng hạn ghi nhận ngữ cảnh, giải phóng tài nguyên hoặc cập nhật trạng thái, nhưng **không có quyền hay khả năng xử lý hoàn toàn lỗi**, nên cần để tầng gọi cao hơn tiếp tục quyết định.

Điểm quyết định là: **`throw e` ném lại đối tượng đang được tham chiếu bởi `e`; nó không tự tạo một exception mới.** Vì vậy đối tượng, loại runtime, message, cause và thông tin stack trace đang có được giữ lại, trừ khi chính mã đã sửa đổi những dữ liệu có thể thay đổi đó. Sau khi lệnh `throw e` chạy, `catch` hiện tại cũng kết thúc bất thường; Java tiếp tục tìm trình xử lý phù hợp ở phạm vi bên ngoài.

Bài này tập trung vào chính lệnh `throw e`: vì sao cần rethrow, đối tượng nào được ném, luồng chạy đi đâu, checked/unchecked exception có ảnh hưởng thế nào, đặc điểm stack trace, quy tắc *precise rethrow* và những sai lầm dễ khiến lỗi gốc bị che mất.

## 1. Cú pháp `throw e` và điều kiện để dùng

Xét đoạn mã:

```java
try {
    riskyOperation();
} catch (IllegalStateException e) {
    System.out.println("Đã ghi nhận lỗi: " + e.getMessage());
    throw e;
}
```

Đây là **đoạn minh họa nằm trong một phương thức**, giả sử `riskyOperation()` đã được định nghĩa. Ý nghĩa:

1. `riskyOperation()` phát sinh một ngoại lệ kiểu `IllegalStateException` hoặc lớp con.
2. `catch` nhận **tham chiếu** đến đối tượng ngoại lệ đó qua biến `e`.
3. Câu lệnh in trong `catch` thực hiện công việc ở tầng hiện tại.
4. `throw e;` ném lại đối tượng mà `e` đang trỏ tới.
5. Java bỏ phần còn lại của handler (nếu có) và lan truyền exception tới phạm vi ngoài. `finally` hoặc đóng tài nguyên liên quan vẫn được thực hiện theo quy tắc.

Cú pháp tổng quát của lệnh `throw` là `throw <expression>;`, trong đó biểu thức phải có kiểu tham chiếu phù hợp với `Throwable` theo quy tắc của Java. `e` thường là tham số của `catch`, nhưng cũng có thể là một biến kiểu exception trong phạm vi khác.

**Phân biệt `throw` và `throws`:**

- `throw e;` là **câu lệnh tại runtime**, thực sự phát sinh việc ném đối tượng.
- `throws IOException` là **khai báo trong chữ ký phương thức**, chỉ ra khả năng phương thức lan truyền một checked exception; bản thân nó không ném đối tượng.
- `try/catch` quyết định có một handler tại chỗ hay không; `throw e` trong handler có nghĩa tầng đó quyết định **không hoàn tất xử lý tại đây**.

Đối với checked exception, trình biên dịch sẽ kiểm tra nghĩa vụ `catch-or-declare` đối với đường đi ném lại. Đối với `RuntimeException` hoặc `Error`, chữ ký `throws` không bắt buộc chỉ vì có `throw e`.

## 2. Vì sao đã bắt exception rồi vẫn ném lại?

Bắt được một exception **không đồng nghĩa** với xử lý xong hậu quả của nó. Một tầng thấp có thể biết chuyện gì vừa xảy ra nhưng không biết nên dừng một request, báo người dùng, chọn tệp khác hay hủy quy trình.

Ví dụ một phương thức nạp cấu hình thất bại vì `IOException`:

- Tầng thao tác tệp biết đường dẫn nào không đọc được.
- Tầng dịch vụ có thể biết chức năng nào đang chờ cấu hình.
- Tầng khởi động ứng dụng có thể quyết định dừng khởi động hoặc thử cấu hình dự phòng.

Nếu tầng dịch vụ chỉ muốn ghi một dấu vết chẩn đoán rồi **giữ nguyên hợp đồng exception**, ném lại chính `IOException` là lựa chọn rõ ràng. Tầng cao hơn sẽ nhận được cùng đối tượng ngoại lệ, thay vì nhận một giá trị “thành công” giả.

Các mục đích hợp lý thường gặp:

| Lý do | Công việc trước `throw e` | Vì sao cần ném tiếp |
| --- | --- | --- |
| Thêm dấu vết chẩn đoán | Ghi ngữ cảnh request hoặc tên thao tác, bảo vệ dữ liệu nhạy cảm | Tầng hiện tại không quyết định được phục hồi |
| Cleanup hoặc cập nhật trạng thái | Hủy công việc cục bộ, đánh dấu một bước thất bại | Caller còn cần biết thao tác tổng thể đã lỗi |
| Giữ nguyên hợp đồng API | Không thay đổi loại exception, message, cause | Caller đã có handler dành cho loại gốc |
| Tập trung chính sách lỗi ở tầng cao | Tầng dưới không sinh phản hồi HTTP/UI | Tầng ranh giới quyết định phản hồi cuối |
| Bảo toàn tín hiệu thất bại | Không trả `null`, `false` hay dữ liệu thay thế không được phép | Tránh khiến caller hiểu lầm thao tác đã hoàn thành |

Không cần viết `catch` chỉ để có `throw e` nếu tầng hiện tại không thực hiện điều gì có ích. Khi không cần xử lý, có thể để ngoại lệ lan truyền tự nhiên; cách đó thường ngắn gọn và giữ nguyên tín hiệu lỗi.

## 3. `throw e` giữ nguyên identity của đối tượng

Java so sánh hai tham chiếu bằng `==` để xác định liệu chúng có trỏ tới **cùng một đối tượng** hay không. Lệnh `throw e` không tạo instance mới, nên tầng bắt bên ngoài nhận đúng instance mà tầng trong đã ném.

Lưu chương trình sau thành **`RethrowIdentityDemo.java`**:

```java
public class RethrowIdentityDemo {
    private static IllegalArgumentException original;

    static void lowLevel() {
        original = new IllegalArgumentException("Mã đầu vào sai");
        throw original;
    }

    static void middleLevel() {
        try {
            lowLevel();
        } catch (IllegalArgumentException e) {
            System.out.println("Giữa: cùng đối tượng = " + (e == original));
            throw e;
        }
    }

    public static void main(String[] args) {
        try {
            middleLevel();
        } catch (IllegalArgumentException e) {
            System.out.println("Ngoài: cùng đối tượng = " + (e == original));
            System.out.println("Loại: " + e.getClass().getSimpleName());
            System.out.println("Message: " + e.getMessage());
        }
        System.out.println("Đã xử lý ở main");
    }
}
```

Biên dịch và chạy bằng Java 17 trở lên:

```bash
javac -encoding UTF-8 --release 17 RethrowIdentityDemo.java
java RethrowIdentityDemo
```

**Stdout chính xác**, với môi trường hiển thị UTF-8:

```text
Giữa: cùng đối tượng = true
Ngoài: cùng đối tượng = true
Loại: IllegalArgumentException
Message: Mã đầu vào sai
Đã xử lý ở main
```

Cả hai biểu thức `e == original` đều là `true`. `middleLevel()` chỉ ném lại đúng tham chiếu nhận được. Đối tượng gốc không bị đổi thành `RuntimeException` hay đối tượng khác.

Biến `original` ở ví dụ chỉ phục vụ kiểm chứng identity; ứng dụng thực không nên lưu exception mới nhất trong trường `static` toàn cục để truyền lỗi giữa các luồng, vì có thể gây tranh chấp và giữ tham chiếu không cần thiết. Kênh truyền lỗi thực sự ở đây là `throw`/ `catch`.

Cũng cần phân biệt: cùng đối tượng **không có nghĩa** toàn bộ thuộc tính bất biến. Một chương trình có thể gọi `setStackTrace(...)`, `fillInStackTrace()` hoặc một số API trên `Throwable` để thay đổi dữ liệu chẩn đoán; nhưng bản thân lệnh `throw e` không tự thực hiện những sửa đổi đó.

## 4. `throw e` thay đổi luồng thực thi như thế nào?

Một exception khi được ném lại phải tiếp tục đi tới **handler bên ngoài** tương thích, hoặc xa hơn trên call stack. Java không thử các nhánh `catch` còn lại của **cùng câu lệnh `try`** như thể chúng là handler của exception vừa phát sinh trong `catch` đầu tiên.

Khung đơn giản:

```text
caller
  └─ service
       └─ try { repository.read(); }
          catch (IOException e) {
              ghi nhận lỗi
              throw e   ───────────┐
          }                      │
  ◀──────────────────────────────┘
  catch (IOException e) { xử lý tại caller }
  tiếp tục sau try/catch của caller
```

Lưu ý những hậu quả:

- Câu lệnh ngay sau `throw e;` theo đường thực thi đó không chạy; nếu đứng trực tiếp sau nó mà không có nhánh điều kiện, Java có thể báo lỗi **unreachable statement** khi biên dịch.
- Mã ở cuối `try` đã bị exception làm gián đoạn cũng không tự được chạy bù.
- Phương thức ném lại exception **thoát bất thường**; caller không thực hiện những lệnh đứng sau lời gọi nằm trong nhánh `try` đã bị ngắt.
- `finally` và các hoạt động dọn dẹp liên quan được xử lý khi rời phạm vi, theo quy tắc Java; nếu chính chúng ném exception mới, tín hiệu cuối cùng có thể thay đổi.
- Khi handler ở tầng ngoài xử lý xong **bình thường**, Java mới tiếp tục từ điểm sau cấu trúc `try/catch` của tầng đó, chứ không nhảy trở lại lời gọi ban đầu.

### Ví dụ có `finally` và vị trí tiếp tục

Lưu thành **`RethrowFlowDemo.java`**:

```java
public class RethrowFlowDemo {
    static void load() {
        throw new IllegalStateException("Không tải được");
    }

    static void process() {
        try {
            System.out.println("1. Trong try");
            load();
            System.out.println("2. Sau load");
        } catch (IllegalStateException e) {
            System.out.println("3. Catch trong");
            throw e;
        } finally {
            System.out.println("4. Finally trong");
        }
    }

    public static void main(String[] args) {
        try {
            process();
            System.out.println("5. Sau process");
        } catch (IllegalStateException e) {
            System.out.println("6. Catch ngoài: " + e.getMessage());
        }
        System.out.println("7. Sau catch ngoài");
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 RethrowFlowDemo.java
java RethrowFlowDemo
```

**Stdout chính xác:**

```text
1. Trong try
3. Catch trong
4. Finally trong
6. Catch ngoài: Không tải được
7. Sau catch ngoài
```

Lệnh “2. Sau load” không chạy vì `load()` ném exception; “5. Sau process” không chạy vì `process()` đã thoát bất thường. `finally` vẫn thực hiện trước khi exception tới `catch` bên ngoài. Cuối cùng, `main` tiếp tục với lệnh số 7.

Đây chính là khác biệt giữa **ghi nhận ở một tầng rồi ném tiếp** và **giải quyết hoàn toàn tại tầng đó**. Nếu `catch` trong `process()` không `throw e` và kết thúc bình thường, lời gọi `process()` có thể trả về bình thường; lúc ấy câu lệnh số 5 có thể chạy, dù công việc `load()` không thành công. Có nên tiếp tục hay không tùy hợp đồng và trạng thái hệ thống.

## 5. Stack trace khi ném lại cùng exception

Một đối tượng `Throwable` thường thu nhận stack trace tại lúc khởi tạo (hoặc khi `fillInStackTrace()` được gọi), nếu cơ chế ghi stack trace được bật cho đối tượng đó. **`throw e` thông thường không tự khởi tạo stack trace mới**. Do đó, tầng ngoài vẫn nhìn thấy dấu vết gắn với exception ban đầu; lệnh `throw e` không được tự thêm làm điểm bắt đầu mới chỉ vì nó ném lại.

Hãy xem một ví dụ xác định đầu stack trace bằng tên phương thức thay vì in số dòng (vì số dòng có thể thay đổi khi sửa mã). Lưu thành **`RethrowTraceDemo.java`**:

```java
public class RethrowTraceDemo {
    static void source() {
        throw new IllegalArgumentException("Nguồn phát sinh");
    }

    static void relay() {
        try {
            source();
        } catch (IllegalArgumentException e) {
            System.out.println("Trong relay: "
                    + e.getStackTrace()[0].getMethodName());
            throw e;
        }
    }

    public static void main(String[] args) {
        try {
            relay();
        } catch (IllegalArgumentException e) {
            System.out.println("Trong main: "
                    + e.getStackTrace()[0].getMethodName());
            System.out.println("Số cause: " + (e.getCause() == null ? 0 : 1));
        }
    }
}
```

Lệnh:

```bash
javac --release 17 RethrowTraceDemo.java
java RethrowTraceDemo
```

**Stdout chính xác** trên Java 17 thông thường:

```text
Trong relay: source
Trong main: source
Số cause: 0
```

Ví dụ cho thấy phần tử đầu của stack trace vẫn chỉ `source` ở cả hai nơi. `relay` đã ném lại bằng `throw e`, nhưng không tự thay đổi dấu vết tại nguồn.

**Lưu ý quan trọng:** không nên phụ thuộc vào định dạng chuỗi `printStackTrace()`, số dòng hoặc nội dung stack trace cụ thể như hợp đồng ổn định giữa các phiên bản và chế độ thực thi JVM. Ngoài ra, `Throwable` cho phép sửa stack trace bằng API, có constructor vô hiệu hóa writable stack trace, và JVM có thể tối ưu một số exception tự phát sinh trong điều kiện đặc biệt. Tính chất chính cần nhớ là **không tạo exception mới** và **không tự gọi `fillInStackTrace()`** chỉ vì `throw e`.

### So sánh với `throw new ...`

Ba cách thường bị nhầm:

| Mã | Đối tượng được ném | Loại nhìn thấy ở caller | Thông tin lỗi gốc |
| --- | --- | --- | --- |
| `throw e;` | **Chính đối tượng `e`** | Loại runtime thực của `e` | Giữ như đang có |
| `throw new IllegalStateException("...", e);` | **Đối tượng mới** | `IllegalStateException` mới | `e` được giữ ở `cause` |
| `throw new IllegalStateException(e.getMessage());` | **Đối tượng mới** | `IllegalStateException` mới | Không giữ `e` ở `cause` nếu không truyền riêng |

`throw e` hợp lý khi caller cần nhận đúng loại và đối tượng ban đầu. **Wrapping** (`throw new SomeException(message, e)`) hợp lý khi tầng hiện tại cần đổi mức trừu tượng hoặc thêm ngữ cảnh vào hợp đồng mới. Nhưng wrapping không còn bảo toàn identity với exception gốc, và caller sẽ thấy stack trace của exception mới, trong khi exception cũ có thể truy ra qua `getCause()`.

Một số tình huống cần giữ nguyên loại checked exception để caller xử lý; lúc đó `throw e` có thể phù hợp hơn wrapping không có chủ đích.

## 6. Ví dụ đối chiếu rethrow với wrapping

Chương trình sau chạy được bằng Java 17+, lưu thành **`RethrowVsWrapDemo.java`**:

```java
public class RethrowVsWrapDemo {
    static IllegalArgumentException createProblem() {
        return new IllegalArgumentException("Giá trị âm");
    }

    static void rethrow(IllegalArgumentException problem) {
        try {
            throw problem;
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    static void wrap(IllegalArgumentException problem) {
        try {
            throw problem;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Không hoàn tất yêu cầu", e);
        }
    }

    public static void main(String[] args) {
        IllegalArgumentException original = createProblem();

        try {
            rethrow(original);
        } catch (IllegalArgumentException e) {
            System.out.println("Rethrow same: " + (e == original));
            System.out.println("Rethrow cause: " + (e.getCause() == null));
        }

        try {
            wrap(original);
        } catch (IllegalStateException e) {
            System.out.println("Wrap same: " + (e == (Object) original));
            System.out.println("Wrap cause same: " + (e.getCause() == original));
            System.out.println("Wrap type: " + e.getClass().getSimpleName());
        }
    }
}
```

Chạy:

```bash
javac --release 17 RethrowVsWrapDemo.java
java RethrowVsWrapDemo
```

**Stdout chính xác:**

```text
Rethrow same: true
Rethrow cause: true
Wrap same: false
Wrap cause same: true
Wrap type: IllegalStateException
```

Ở phần rethrow, exception đúng là `original`, và `getCause()` vẫn `null`, vì mã không thiết lập cause. Ở phần wrapping, caller bắt một `IllegalStateException` mới; `getCause()` của nó trỏ về `original`, nên có thể lần theo chuỗi nguyên nhân.

Phép so sánh `e == (Object) original` ở ví dụ được ép về `Object` vì `e` có kiểu `IllegalStateException` và `original` có kiểu `IllegalArgumentException`, hai lớp anh em trong nhánh `RuntimeException`; ép về `Object` cho phép so sánh identity minh họa. Kết quả vẫn `false` vì đối tượng wrapper được tạo mới.

Đây là lý do `throw e` và `throw new ...` **khác nhau về hợp đồng, kiểu và dấu vết chẩn đoán**, dù cả hai đều khiến luồng thực thi hiện tại kết thúc bất thường.

## 7. Ném lại checked exception: `throws` vẫn phải phù hợp

Với checked exception, `throw e` vẫn là một đường có thể phát sinh checked exception tại thời điểm biên dịch. Nếu handler bắt `IOException` rồi ném lại, phương thức chứa nó phải **bắt tiếp bên trong một cấu trúc khác phù hợp hoặc khai báo `throws IOException` (hay kiểu cha được phép)**.

Lưu thành **`CheckedRethrowDemo.java`**:

```java
import java.io.IOException;

public class CheckedRethrowDemo {
    static void readConfig() throws IOException {
        throw new IOException("Không đọc được cấu hình");
    }

    static void loadService() throws IOException {
        try {
            readConfig();
            System.out.println("Đã đọc cấu hình");
        } catch (IOException e) {
            System.out.println("Dịch vụ ghi nhận: " + e.getMessage());
            throw e;
        }
    }

    public static void main(String[] args) {
        try {
            loadService();
        } catch (IOException e) {
            System.out.println("Main xử lý: " + e.getMessage());
        }
        System.out.println("Main vẫn hoạt động");
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 --release 17 CheckedRethrowDemo.java
java CheckedRethrowDemo
```

**Stdout chính xác:**

```text
Dịch vụ ghi nhận: Không đọc được cấu hình
Main xử lý: Không đọc được cấu hình
Main vẫn hoạt động
```

`IOException` là checked exception. Ở `loadService()`, dù đã viết `catch (IOException e)`, việc ném lại khiến phương thức vẫn có khả năng trả lỗi `IOException` ra ngoài, nên chữ ký khai báo `throws IOException`. `main()` xử lý lỗi ở nơi cao hơn.

Nếu bỏ `throws IOException` khỏi `loadService()` mà vẫn giữ `throw e`, mã sẽ gặp lỗi biên dịch vì checked exception chưa được xử lý/khai báo. Ngược lại, nếu handler bắt và xử lý hoàn toàn mà không ném tiếp, tùy phần mã còn lại, khai báo `throws` có thể không cần thiết.

Với unchecked exception như `IllegalStateException`, không có nghĩa vụ khai báo tương tự, kể cả khi được ném lại nhiều tầng.

## 8. Tính năng *precise rethrow* từ Java 7

Một điểm ít hiển nhiên: Java có cơ chế **phân tích chính xác các loại checked exception được ném lại** khi tham số `catch` là **final hoặc effectively final**. Điều này thường được gọi là *more precise rethrow analysis*.

Trước hết xét ví dụ Java 17 hợp lệ, lưu thành **`PreciseRethrowDemo.java`**:

```java
import java.io.IOException;

public class PreciseRethrowDemo {
    static void read() throws IOException {
        throw new IOException("Hỏng tệp");
    }

    // Dù catch kiểu Exception, Java nhận biết ở đây
    // checked exception có thể thoát ra là IOException.
    static void relay() throws IOException {
        try {
            read();
        } catch (Exception e) {
            System.out.println("Relay: " + e.getClass().getSimpleName());
            throw e;
        }
    }

    public static void main(String[] args) {
        try {
            relay();
        } catch (IOException e) {
            System.out.println("Main: " + e.getMessage());
        }
    }
}
```

Chạy:

```bash
javac --release 17 PreciseRethrowDemo.java
java PreciseRethrowDemo
```

**Stdout chính xác:**

```text
Relay: IOException
Main: Hỏng tệp
```

Tại sao `relay()` chỉ khai báo `throws IOException`, thay vì `throws Exception`? Vì trình biên dịch phân tích những exception có thể phát sinh từ thân `try`. Trong ví dụ, checked exception liên quan là `IOException`. Biến `e` không được gán lại nên đáp ứng điều kiện precise rethrow; phần unchecked exception nếu có không phải khai báo. Java không buộc chữ ký phải mở rộng thành `throws Exception` chỉ vì khai báo `catch (Exception e)`.

Điều kiện quan trọng:

1. `e` là tham số của `catch` được ném lại trong handler.
2. `e` là `final` hoặc **effectively final**: không bị gán lại ở thân `catch`.
3. Compiler xác định các loại exception có thể phát sinh từ khối `try` mà handler này có khả năng bắt, rồi áp dụng quy tắc precise rethrow theo ngôn ngữ.

Nếu gán lại tham số `e`, quy tắc precise rethrow thu hẹp kiểu không còn áp dụng như trước; `throw e` có thể bị xét theo kiểu khai báo rộng hơn của tham số. Vì vậy đoạn sau **cố ý không biên dịch** khi phương thức chỉ khai báo `throws IOException`:

```java
// Phản ví dụ cú pháp trong lớp có import java.io.IOException:
static void changedCatchVariable() throws IOException {
    try {
        throw new IOException("Lỗi A");
    } catch (Exception e) {
        e = new IOException("Lỗi B"); // e bị gán lại
        throw e; // Có thể bị yêu cầu xử lý/khai báo kiểu Exception
    }
}
```

Để giữ nguyên đối tượng lỗi gốc và tận dụng precise rethrow, **không gán lại tham số `catch`**. Thường cũng không có lý do hợp lý để đổi `e` thành một instance mới rồi gọi đó là “ném lại cùng đối tượng”.

### Multi-catch và tham số ngầm định final

Java cho phép bắt nhiều loại exception độc lập trong một `catch` bằng `|`. Tham số của multi-catch là **implicitly final**, không thể gán lại.

```java
import java.io.IOException;

class MultiCatchExample {
    static void run(boolean ioFailure) throws IOException {
        try {
            if (ioFailure) {
                throw new IOException("I/O");
            }
            throw new IllegalArgumentException("Tham số");
        } catch (IOException | IllegalArgumentException e) {
            throw e;
        }
    }
}
```

Đây là lớp minh họa **không có `main`**. `run` khai báo `IOException` vì đó là checked exception, còn `IllegalArgumentException` là unchecked và không đòi hỏi khai báo. Một `catch` multi-catch vẫn chỉ nhận đúng **một đối tượng** tại runtime; `throw e` ném lại đối tượng đó.

Không thể dùng multi-catch với hai loại mà một loại là lớp con của loại còn lại (ví dụ `IOException | Exception`) vì nhánh kiểu chồng lắp không hợp lệ.

## 9. Không được nhầm ném lại với thử lại thao tác

Hai từ tiếng Anh *rethrow* và *retry* tương tự nhau nhưng ý nghĩa hoàn toàn khác:

- **Rethrow**: ném lại **tín hiệu ngoại lệ** để tầng khác xử lý. Lệnh gây lỗi trước đó không tự chạy lại.
- **Retry**: chủ động **thực hiện lại thao tác** (gọi lại method, lặp hoặc gửi lại yêu cầu), thường sau khi kiểm tra lỗi có tính tạm thời.

Ví dụ:

```java
try {
    saveOrder();
} catch (IllegalStateException e) {
    recordFailure(e);
    throw e; // Không gọi lại saveOrder()
}
```

Đây là khung minh họa. Nếu `saveOrder()` đã viết dữ liệu rồi thất bại lúc trả kết quả, việc retry thiếu kiểm soát có thể tạo dữ liệu trùng. `throw e` chỉ chuyển lỗi đi, không lặp giao dịch.

Khi cần retry thật sự, phải xác định loại lỗi có thể phục hồi, số lần retry, timeout/backoff, khả năng idempotent của thao tác và side effect đã hoàn thành. Dùng `throw e` ở điểm hết khả năng xử lý nếu vẫn không thành công, chứ không nhầm nó là cơ chế retry.

## 10. `throw e` trong `catch` có tương tác thế nào với `finally`?

Một `finally` gắn với `try` thường thực hiện khi rời `try/catch`, kể cả khi `catch` ném lại exception:

```java
try {
    operation();
} catch (RuntimeException e) {
    auditFailure(e);
    throw e;
} finally {
    releaseLocalState();
}
```

Đây là mã minh họa; các phương thức phụ trợ cần được định nghĩa để chạy. Khi `throw e` xảy ra, `releaseLocalState()` được gọi trong quá trình rời cấu trúc, và sau đó exception tiếp tục lan truyền **nếu `finally` hoàn tất bình thường**.

Hai cảnh báo thiết yếu:

- Nếu `finally` ném exception mới, lỗi mới có thể trở thành exception thoát ra, **che khuất lỗi gốc** đang được ném lại.
- Nếu `finally` thực hiện `return`, nó có thể ghi đè việc ném exception, làm caller không còn thấy lỗi ban đầu.

Vì vậy hạn chế đặt `return` hoặc `throw` không chủ đích trong `finally`. Khi xử lý tài nguyên `AutoCloseable`, ưu tiên `try-with-resources` để có thứ tự đóng rõ ràng và giữ được suppressed exception khi cả thân `try` lẫn `close()` cùng thất bại.

## 11. Rethrow kết hợp với `try-with-resources`

Đối với tài nguyên `AutoCloseable`, rethrow không miễn trách nhiệm cleanup. Khi rời khối `try-with-resources`, các tài nguyên khởi tạo thành công được đóng theo **thứ tự ngược**. Tùy vị trí `catch`, tài nguyên có thể đã được đóng trước khi handler nhận lỗi.

Lưu chương trình Java 17+ thành **`RethrowResourceDemo.java`**:

```java
public class RethrowResourceDemo {
    static class Resource implements AutoCloseable {
        @Override
        public void close() {
            System.out.println("Đóng tài nguyên");
        }
    }

    static void work() {
        try (Resource resource = new Resource()) {
            throw new IllegalArgumentException("Dữ liệu lỗi");
        } catch (IllegalArgumentException e) {
            System.out.println("Ghi nhận trong work");
            throw e;
        }
    }

    public static void main(String[] args) {
        try {
            work();
        } catch (IllegalArgumentException e) {
            System.out.println("Main: " + e.getMessage());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 RethrowResourceDemo.java
java RethrowResourceDemo
```

**Stdout chính xác:**

```text
Đóng tài nguyên
Ghi nhận trong work
Main: Dữ liệu lỗi
```

Tài nguyên được đóng **trước** khi thực hiện `catch` trực tiếp gắn với `try-with-resources`. Sau đó `throw e` chuyển tiếp đúng exception ban đầu lên `main`.

Nếu `close()` cũng ném exception trong lúc thân `try` đã ném một exception khác, lỗi đóng thường được lưu trong `e.getSuppressed()` của exception chính. Lệnh `throw e` giữ nguyên đối tượng chính (cùng danh sách suppressed đang có), nên tầng ngoài có thể kiểm tra cả lỗi chính lẫn lỗi đóng. Nếu muốn hỗ trợ chẩn đoán toàn diện, không bỏ qua suppressed exceptions một cách máy móc.

## 12. Những sai lầm thường gặp khi dùng `throw e`

### 12.1. Viết `catch` chỉ để ném lại

```java
try {
    process();
} catch (Exception e) {
    throw e;
}
```

Đây là khung minh họa, với yêu cầu `throws` phù hợp nếu có checked exception. Nếu handler không ghi nhận ngữ cảnh, không dọn dẹp riêng và không chuyển đổi hợp đồng, khối `catch` thường không tạo giá trị mới. Có thể để exception lan tự nhiên; cleanup đặt trong `try-with-resources` hoặc `finally` khi thực sự cần.

### 12.2. Ghi log trùng ở từng tầng

```text
Repository: ghi đầy đủ stack trace rồi throw e
Service: ghi đầy đủ stack trace rồi throw e
Controller: ghi đầy đủ stack trace rồi trả lỗi
```

Một lỗi có thể thành ba bản ghi dài, khó theo dõi và tốn chi phí vận hành. Chọn nơi ghi log có đủ ngữ cảnh, dùng correlation/request ID khi phù hợp, và cân nhắc tránh log trùng. Rethrow giữ nguyên exception, nhưng **không buộc** mọi tầng phải ghi log.

### 12.3. Gọi `throw new ...` rồi tưởng vẫn là cùng đối tượng

`throw new RuntimeException(e)` tạo **wrapper mới** với `e` làm cause. Nó không tương đương `throw e`. Nếu mục tiêu chỉ là lan truyền nguyên trạng, sử dụng `throw e`.

### 12.4. Gán lại `e` rồi ném

```java
// Ý tưởng sai nếu mục tiêu là giữ đúng exception ban đầu:
catch (IllegalArgumentException e) {
    e = new IllegalArgumentException("Thông điệp khác");
    throw e;
}
```

Đây là phần minh họa trong `try/catch`, không phải chương trình độc lập. Handler trên ném một đối tượng **mới**, đã mất identity của lỗi ban đầu và không tự giữ cause/stack trace cũ. Với checked exception, việc gán lại biến `catch` cũng có thể làm mất cơ chế precise rethrow.

### 12.5. Làm mất nguyên nhân ở tầng bọc lỗi

Nếu cần đổi loại exception, phải chủ động giữ nguyên nhân:

```java
// Không giữ nguyên nhân:
throw new IllegalStateException("Không thể lưu đơn hàng");

// Giữ nguyên nhân (giả sử e đang là exception gốc):
throw new IllegalStateException("Không thể lưu đơn hàng", e);
```

Đây là hai lựa chọn **minh họa độc lập**, không đặt liên tiếp trong một khối thực thi. Cách thứ hai tạo wrapper mới nhưng giữ `e` trong `cause`. Khi chỉ cần lan truyền nguyên bản, `throw e` vẫn là cách giữ thông tin trực tiếp nhất.

### 12.6. Bắt sai loại hoặc nuốt cả `Error`

Không dùng `catch (Throwable e)` rồi `throw e` chỉ để “bắt mọi thứ” khi không có lý do kiến trúc rõ ràng. `Throwable` bao gồm cả `Error`, một số loại như `OutOfMemoryError` có thể khiến hệ thống ở trạng thái không phù hợp để tiếp tục công việc. Nếu bắt rộng tại một ranh giới giám sát đặc biệt, xử lý và chuyển tiếp phải đúng hợp đồng; mã nghiệp vụ thông thường nên chọn loại cụ thể.

### 12.7. Hiểu sai stack trace

`throw e` không tự tạo stack trace ở vị trí rethrow. Vì vậy nếu log chỉ dựa vào dòng đầu của stack trace để tìm “điểm rethrow”, có thể bỏ sót nơi tầng trung gian quyết định chuyển lỗi. Khi cần biết thêm ngữ cảnh, có thể ghi log ở đúng ranh giới hoặc wrapping có cause theo hợp đồng. Không gọi `fillInStackTrace()` chỉ để giả vờ lỗi gốc phát sinh ở một nơi khác.

### 12.8. Dùng rethrow để xử lý mọi kết quả nghiệp vụ

Một đơn hàng hết hàng, mã giảm giá hết hạn hoặc tìm kiếm không có kết quả có thể là **kết quả nghiệp vụ dự kiến**, không phải lỗi kỹ thuật phải ném xuyên các tầng. Nếu hợp đồng trả về kết quả có trạng thái, hãy giữ mô hình đó; chỉ dùng exception khi thực sự thích hợp với ý nghĩa và trách nhiệm xử lý của API.

## 13. Chọn `throw e`, `throw new ...`, hay xử lý tại chỗ

Câu hỏi nên hỏi là: **Tầng hiện tại đã xử lý xong hậu quả của exception chưa, và caller kỳ vọng loại tín hiệu nào?**

| Tình huống | Lựa chọn thường phù hợp | Hệ quả |
| --- | --- | --- |
| Không có công việc gì cần làm ở tầng này | Không tạo `catch` dư thừa | Exception tự lan truyền |
| Cần ghi nhận ngữ cảnh nhưng giữ loại lỗi nguyên bản | `catch (... e) { ...; throw e; }` | Cùng đối tượng lan lên caller |
| Tầng hiện tại biết cách phục hồi an toàn | `catch` và xử lý hoàn toàn, không rethrow | Lời gọi có thể hoàn tất bình thường |
| API ở tầng cao cần lỗi có mức trừu tượng khác | `throw new DomainOrServiceException(..., e)` | Tạo exception mới, giữ lỗi gốc ở cause |
| Lỗi là kết quả dự kiến của nghiệp vụ | Trả giá trị/kiểu kết quả rõ ràng nếu hợp đồng chọn cách đó | Caller xử lý theo nhánh bình thường |
| Checked exception vẫn cần đi qua tầng trung gian | `throw e` và `throws` tương ứng | Nghĩa vụ checked exception vẫn còn |
| Cần kiểm soát retry | Viết chính sách retry có giới hạn và hiểu side effect; hết khả năng thì rethrow | Retry là hành động mới, không phát sinh do `throw e` |

Một nguyên tắc thiết kế hữu ích: **không dùng rethrow để che giấu trách nhiệm, nhưng cũng không ép tầng thấp tự quyết định phản hồi mà nó không có đủ thông tin**. Caller cần một tín hiệu chính xác để biết thao tác đã hoàn tất hay vẫn đang lỗi.

## 14. Câu hỏi kiểm tra kiến thức

**Câu 1.** Trong `catch (IOException e) { throw e; }`, `throw e` có tạo `IOException` mới không?  
**Đáp án:** Không. Nó ném lại đúng đối tượng được tham chiếu bởi `e`. Caller có thể nhận chính cùng instance đó.

**Câu 2.** Sau khi `throw e` ở handler, câu lệnh nằm ngay sau nó trong handler có chạy không?  
**Đáp án:** Không theo đường thực thi đó. Exception lại lan truyền ra ngoài; đoạn mã trực tiếp không thể tới có thể bị compiler báo lỗi.

**Câu 3.** `throw e` có thay đổi message, cause và stack trace không?  
**Đáp án:** Bản thân lệnh không tạo object mới hay tự thay đổi chúng. Mã khác có thể đã thay đổi thuộc tính được phép sửa của object, và một số cơ chế JVM có ngoại lệ về biểu diễn stack trace.

**Câu 4.** `catch (Exception e) { throw e; }` có luôn buộc phương thức khai báo `throws Exception` không?  
**Đáp án:** Không. Khi `e` là `final` hoặc effectively final, quy tắc precise rethrow có thể cho phép compiler thu hẹp tập checked exception có thể ném từ `try`; ví dụ chỉ cần `throws IOException` nếu đó là loại checked liên quan.

**Câu 5.** Tại sao `throw new IllegalStateException("...", e)` không tương đương `throw e`?  
**Đáp án:** Nó tạo instance mới, đổi loại top-level và giữ `e` làm cause; `throw e` giữ nguyên instance và loại runtime của exception ban đầu.

**Câu 6.** Nếu `finally` ném exception mới trong lúc `catch` đang thực hiện `throw e` thì caller nhận exception nào?  
**Đáp án:** Exception mới từ `finally` có thể thay thế exception đang được ném lại, làm lỗi ban đầu bị che. Đó là lý do không ném lỗi mới vô ý trong cleanup.

**Câu 7.** Nếu handler của `try-with-resources` ném lại bằng `throw e`, tài nguyên đã đóng chưa?  
**Đáp án:** Các tài nguyên được khai báo trong chính `try-with-resources` đã được cố gắng đóng trước khi handler `catch` gắn với cấu trúc đó chạy; lỗi đóng có thể được ghi dạng suppressed.

**Câu 8.** `throw e` có tự thử lại thao tác vừa lỗi không?  
**Đáp án:** Không. Rethrow chuyển tín hiệu lỗi lên tầng khác; retry cần mã gọi lại thao tác theo chính sách.

**Câu 9.** Ném lại unchecked exception có bắt buộc khai báo `throws` không?  
**Đáp án:** Không. `RuntimeException`, `Error` và các lớp con là unchecked, bất kể ném lần đầu hay ném lại.

**Câu 10.** Nếu `catch` không thể khôi phục, không có thông tin bổ sung và chỉ gọi `throw e`, có nên giữ handler đó không?  
**Đáp án:** Thường không cần. Để exception tự lan truyền sẽ đơn giản hơn, miễn là cleanup và hợp đồng được bảo đảm ở nơi phù hợp.

## 15. Checklist khi review mã có `throw e`

Khi đọc một handler ném lại ngoại lệ, tự kiểm tra:

1. **Nguồn lỗi:** Exception này xuất phát từ thao tác nào, loại runtime thực là gì, có checked hay unchecked?
2. **Công việc handler:** Tầng này làm gì thực sự có ích trước khi ném lại? Có đang log cùng lỗi quá nhiều lần không?
3. **Identity và dữ liệu:** Có giữ đúng `e` hay vô tình tạo/gán một instance mới? Message, cause, stack trace, suppressed có bị sửa gây khó chẩn đoán không?
4. **Hợp đồng:** Phương thức đã khai báo các checked exception cần thiết chưa? Có tận dụng precise rethrow an toàn mà không gán lại tham số `catch` không?
5. **Luồng thực thi:** Có lệnh nào sau điểm ném bị bỏ qua? Caller xử lý ở đâu, và liệu `finally`/cleanup có thể che exception gốc?
6. **Phục hồi:** Caller có khả năng xử lý lỗi hay chỉ tiếp tục ném vô tận? Có điểm chịu trách nhiệm cuối cùng ở ranh giới ứng dụng không?
7. **Nguồn dữ liệu:** Đây là tình huống lỗi bất thường hay một kết quả nghiệp vụ hợp lệ nên biểu diễn bằng giá trị rõ ràng?
8. **Kiểm thử:** Đã thử đường ném lại, đường bắt ở caller, cleanup khi lỗi, và checked exception trong chữ ký chưa?

## 16. Kết luận

**`throw e` ném lại cùng đối tượng exception đã được bắt.** Nó bảo toàn identity và loại runtime của exception gốc, thường giữ nguyên thông tin message, cause và stack trace, đồng thời làm handler hiện tại kết thúc bất thường để lỗi tiếp tục đi lên tầng có thể xử lý. Lệnh này **không** tự retry thao tác, **không** tạo wrapper mới và **không** có nghĩa tầng hiện tại đã phục hồi thành công.

Khi exception là checked, vẫn phải tuân thủ `catch-or-declare`; cơ chế *precise rethrow* của Java cho phép một số handler bắt kiểu rộng nhưng khai báo tập checked exception hẹp hơn, nếu tham số `catch` không bị gán lại. Khi cần đổi tầng trừu tượng hoặc thông điệp, hãy dùng wrapper mới **có cause** thay vì nhầm nó với rethrow nguyên bản. Và khi không có việc gì cần làm trước rethrow, để exception tự lan truyền thường là thiết kế gọn hơn.

Nguyên tắc quan trọng để quyết định là: **xử lý khi biết cách phục hồi; ném lại khi phải giữ lỗi gốc cho tầng có trách nhiệm; bọc lỗi khi hợp đồng ở tầng mới yêu cầu một loại tín hiệu khác.**


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="throw-statement-007">Đường thực thi không thể tới sau throw</a>

<details>
<summary>Xem chi tiết</summary>

# Đường thực thi không thể tới sau `throw` trong Java

Câu lệnh `throw` **kết thúc đường thực thi bình thường tại vị trí nó được thực thi**. Quyền điều khiển chuyển sang cơ chế tìm và xử lý ngoại lệ: có thể vào một `catch` phù hợp, thực hiện `finally` hoặc truyền ngoại lệ ra phương thức gọi. Vì vậy, câu lệnh viết **ngay sau một `throw` vô điều kiện trong cùng khối** không có đường đi hợp lệ đến nó và sẽ bị trình biên dịch Java từ chối với lỗi dạng `unreachable statement`.

Tuy nhiên, cần phân biệt ba nhận định:

- **`throw` luôn hoàn tất đột ngột** (*always completes abruptly*), theo Java Language Specification (JLS) §14.18.
- **Một câu lệnh có thể tới được hay không** (*reachability*) là quy tắc phân tích tĩnh của trình biên dịch, theo JLS §14.22.
- **Một dòng có được thực thi trong một lần chạy cụ thể hay không** là quan sát động, không nhất thiết trùng hoàn toàn với quy tắc reachability.

Phạm vi bài này là tác động của `throw` đối với các câu lệnh kế tiếp, cách trình biên dịch xác định câu lệnh không thể tới và những nơi ngoại lệ được bắt để chương trình tiếp tục.

## 1. Vì sao `throw` chặn đường chạy phía sau?

Cú pháp:

```java
throw expression;
```

Biểu thức phải cho ra giá trị tham chiếu gán được cho `Throwable` (hoặc `null`, gây `NullPointerException` khi chạy). Khi biểu thức được đánh giá thành một đối tượng ngoại lệ, `throw` ném đối tượng đó và **hoàn tất đột ngột** với lý do `throw`. Nếu bản thân biểu thức đánh giá đã ném một ngoại lệ khác, `throw` cũng hoàn tất đột ngột theo lý do đó. Không có trường hợp câu lệnh `throw` hoàn tất bình thường để chuyển thẳng sang câu lệnh kế tiếp.

Ví dụ sau **cố ý không biên dịch**:

```java
class InvalidAfterThrow {
    static void fail() {
        throw new IllegalStateException("Dừng tại đây");
        // System.out.println("Không tới được"); // Nếu bỏ comment: lỗi biên dịch.
    }
}
```

Nếu bỏ dấu comment ở câu lệnh `System.out.println(...)`, `javac` từ chối câu lệnh đó vì **unreachable statement**. Bản thân phiên bản đang để comment biên dịch được; ví dụ nhằm minh họa điểm lỗi chứ không phải tạo đầu ra khi chạy.

Tại sao? Trong cùng khối `{ ... }`, câu lệnh kế tiếp chỉ *reachable* nếu câu lệnh trước **có thể hoàn tất bình thường** (*can complete normally*). `throw` không bao giờ có khả năng này, nên câu lệnh tiếp theo không thỏa điều kiện reachability.

```text
Khối phương thức
    |
    v
throw new IllegalStateException(...)
    |
    +-- hoàn tất đột ngột --> tìm catch / tháo stack / finally
    |
    X  Không có cạnh điều khiển bình thường đến dòng tiếp theo
       System.out.println(...)
```

Không thể “sửa” lỗi này bằng cách đặt thêm `return;` phía sau `throw`: chính `return` cũng không thể tới. Không thể yêu cầu trình biên dịch bỏ qua lỗi reachability chỉ bằng cách ghi chú rằng đó là mã dự phòng.

## 2. `Unreachable statement` là lỗi biên dịch, khác ngoại lệ lúc chạy

Java phân tích reachability khi **biên dịch** dựa trên cấu trúc các câu lệnh. Nó không cần chạy chương trình để xác định đường đi của một `throw` vô điều kiện. Chẳng hạn:

```java
class AnotherInvalidExample {
    static int parse() {
        throw new IllegalArgumentException("Bad input");
        // return 42; // Nếu mở comment sẽ là unreachable statement.
    }
}
```

Phương thức trả `int` không yêu cầu bổ sung `return` sau `throw`, vì phương thức có thể kết thúc bằng ngoại lệ thay vì trả một giá trị. Câu lệnh `throw` là con đường kết thúc hợp lệ. Cần đặt điều kiện để chỉ ném ở trường hợp lỗi, rồi trả giá trị ở trường hợp hợp lệ.

**Hai chẩn đoán không được nhầm nhau:**

| Hiện tượng | Ví dụ | Bản chất |
| --- | --- | --- |
| `unreachable statement` | Có câu lệnh trong cùng khối ngay sau `throw` vô điều kiện. | Lỗi reachability lúc biên dịch. |
| `missing return statement` | Phương thức `int` có đường đi rơi xuống cuối thân phương thức mà không `return` hay `throw`. | Kiểm tra mọi đường đi bắt buộc trả giá trị hoặc kết thúc đột ngột. |
| `unreported exception ... must be caught or declared to be thrown` | Ném checked exception nhưng không `catch` hoặc khai báo `throws` thích hợp. | Kiểm tra ngoại lệ checked, độc lập với reachability. |
| `NullPointerException` tại `throw null;` | Biểu thức `throw` được đánh giá thành `null` khi chạy. | Ngoại lệ runtime; `throw` vẫn hoàn tất đột ngột. |

Nội dung nguyên văn và vị trí lỗi trình biên dịch có thể thay đổi theo phiên bản JDK. Quan trọng là nguyên nhân và cách sửa.

Ví dụ về checked exception (đoạn sau **cố ý không hợp lệ**):

```java
import java.io.IOException;

class CheckedExceptionExample {
    static void load() {
        throw new IOException("Read failed"); // Phải bắt hoặc khai báo throws.
    }
}
```

Đoạn này không gặp lỗi “unreachable statement”, vì không có câu lệnh phía sau `throw`. Lỗi nằm ở hợp đồng checked exception. Có thể sửa khai báo thành `static void load() throws IOException` rồi để bên gọi chịu trách nhiệm bắt/khai báo; hoặc xử lý ngoại lệ tại `catch` có ngữ cảnh phù hợp.

## 3. `throw` có điều kiện: các nhánh hợp lệ vẫn được đi tiếp

Một nhánh `if` có thể ném, còn đường khác vẫn có khả năng chạy tới câu lệnh kế tiếp:

```java
static int requirePositive(int value) {
    if (value <= 0) {
        throw new IllegalArgumentException("value phải dương");
    }
    return value * 2;
}
```

Đây là phương thức minh họa nằm trong một lớp. Với `value = -1`, câu lệnh `throw` được chạy và `return` không chạy. Với `value = 5`, điều kiện sai, khối `if` được bỏ qua và phương thức trả `10`. Vì có đường đi hợp lệ, `return value * 2;` là **reachable**.

Khi cả hai nhánh cùng không thể hoàn tất bình thường, câu lệnh ngay sau `if-else` có thể không reachable. Ví dụ **cố ý không biên dịch nếu mở comment**:

```java
class InvalidBothBranches {
    static void fail(boolean mode) {
        if (mode) {
            throw new IllegalArgumentException("A");
        } else {
            throw new IllegalStateException("B");
        }
        // System.out.println("after"); // Nếu mở comment: unreachable statement.
    }
}
```

Mỗi nhánh đều ném ngoại lệ, nên không có đường hoàn tất bình thường qua `if-else`. Khi một nhánh trả giá trị hoặc dùng `throw`, cần xem **toàn bộ cấu trúc `if`**, không chỉ câu lệnh cuối trong một nhánh.

Một mẫu thực tế thường gặp là *guard clause*:

```java
static String normalizeName(String name) {
    if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("Tên không được để trống");
    }
    return name.trim();
}
```

Cách này đưa đường lỗi ra khỏi luồng chính, nên phần xử lý trường hợp hợp lệ có thể đọc tuần tự. Đặt điều kiện kiểm tra **trước** khi dùng giá trị để tránh phát sinh ngoại lệ thiếu ngữ cảnh (ví dụ `NullPointerException`). Đoạn này là phương thức trong một lớp, không có `main` để chạy độc lập.

## 4. Ví dụ Java 17+ chạy được: guard clause, `throw` và `catch`

Lưu tệp `ThrowReachabilityDemo.java`:

```java
public class ThrowReachabilityDemo {
    static int parsePositive(String input) {
        final int value;

        try {
            value = Integer.parseInt(input);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Not a number: " + input, ex);
        }

        if (value <= 0) {
            throw new IllegalArgumentException("Must be positive");
        }
        return value;
    }

    static void runTask(int kind) {
        try {
            if (kind == 0) {
                throw new IllegalStateException("stop");
            }
            System.out.println("try-ok");
        } catch (IllegalStateException ex) {
            System.out.println("caught=" + ex.getMessage());
        } finally {
            System.out.println("finally");
        }
        System.out.println("after-try");
    }

    public static void main(String[] args) {
        System.out.println("positive=" + parsePositive("7"));

        try {
            parsePositive("-2");
        } catch (IllegalArgumentException ex) {
            System.out.println("invalid=" + ex.getMessage());
        }

        runTask(0);
        runTask(1);
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac -encoding UTF-8 ThrowReachabilityDemo.java
java ThrowReachabilityDemo
```

Kết quả:

```text
positive=7
invalid=Must be positive
caught=stop
finally
after-try
try-ok
finally
after-try
```

**Phân tích:**

1. `parsePositive("7")` không ném vì điều kiện `value <= 0` sai; kết quả `7` được trả bình thường.
2. `parsePositive("-2")` ném `IllegalArgumentException`. Câu lệnh `return value;` **vẫn reachable xét về mặt biên dịch**, nhưng không chạy trong lần gọi này. `catch` bên ngoài xử lý ngoại lệ.
3. `runTask(0)` ném trong `try` và chuyển đến `catch`. Phần `try-ok` bị bỏ qua, `finally` được chạy, rồi `after-try` chạy vì cấu trúc `try-catch-finally` đã hoàn tất bình thường.
4. `runTask(1)` không ném: `try-ok` được in, `catch` được bỏ qua, `finally` vẫn chạy và sau đó `after-try` chạy.

Lỗi **unreachable statement** là khái niệm tĩnh; kết quả của một lần gọi với tham số cụ thể không tự biến một câu lệnh hợp lệ thành lỗi biên dịch.

## 5. Vì sao sau `try-catch` vẫn có thể tới được?

Ví dụ:

```java
try {
    throw new IllegalArgumentException("bad");
} catch (IllegalArgumentException ex) {
    System.out.println("handled");
}
System.out.println("after");
```

Đây là đoạn minh họa đặt trong thân phương thức. Trình tự thực thi bình thường là:

```text
throw "bad"
    |
    v
catch (IllegalArgumentException)
    |  in "handled"
    v
sau try-catch
       in "after"
```

Dòng `after` nằm **ngoài** khối `try` chứa `throw`. `catch` bắt và xử lý lý do hoàn tất đột ngột; vì `catch` hoàn tất bình thường, cả cấu trúc `try-catch` có thể hoàn tất bình thường, khiến câu lệnh kế tiếp hợp lệ.

So sánh mã sau (có lỗi nếu mở comment):

```java
try {
    throw new IllegalArgumentException("bad");
    // System.out.println("in try"); // unreachable statement nếu mở comment
} catch (IllegalArgumentException ex) {
    System.out.println("handled");
}
System.out.println("after"); // reachable
```

Phần `catch` không làm cho dòng ở **trong cùng khối `try`, ngay sau `throw`** trở nên reachable. Nó chỉ tạo một đường tiếp tục hợp lệ sau khi khối `try` đã hoàn tất đột ngột.

Đồng thời, `catch` phải tương thích kiểu ngoại lệ. Nếu chỉ có `catch (NumberFormatException ex)`, nó không bắt được `IllegalArgumentException` thông thường do lập trình viên ném (mặc dù `NumberFormatException` là lớp con của `IllegalArgumentException`). Trong ví dụ ném `new IllegalArgumentException(...)`, loại đối tượng là `IllegalArgumentException`, nên nhánh chỉ bắt `NumberFormatException` không phù hợp. Đường ngoại lệ tiếp tục lan ra.

## 6. `finally` được chạy nhưng không mở lại đường phía sau `throw`

Khi chuyển quyền điều khiển từ `try` vì `throw`, Java thực hiện `finally` tương ứng trong quá trình chuyển quyền, theo quy tắc của `try-finally` và `try-catch-finally`. Nhưng `finally` **không quay ngược vào phần phía sau `throw` trong khối `try`**.

Chương trình chạy được, lưu thành `FinallyThrowDemo.java`:

```java
public class FinallyThrowDemo {
    static void process() {
        try {
            System.out.println("before throw");
            throw new IllegalStateException("failed");
        } finally {
            System.out.println("cleanup");
        }
    }

    public static void main(String[] args) {
        try {
            process();
        } catch (IllegalStateException ex) {
            System.out.println("caught=" + ex.getMessage());
        }
        System.out.println("after call");
    }
}
```

```bash
javac -encoding UTF-8 FinallyThrowDemo.java
java FinallyThrowDemo
```

Kết quả:

```text
before throw
cleanup
caught=failed
after call
```

Điểm cần ghi nhớ: `process()` không hoàn tất bình thường vì ngoại lệ thoát ra ngoài; nhưng lời gọi nằm trong `try-catch` của `main`, và `main` có thể tiếp tục tại `after call` sau khi bắt lỗi. Không có lý do nào cho phép thêm một câu lệnh bình thường ngay sau `throw` trong cùng khối `try`.

**Cảnh báo về `finally`:** Nếu `finally` tự thực thi `throw` hoặc `return`, lý do hoàn tất mới của nó có thể **thay thế** ngoại lệ đang được truyền. Tránh `return` trong `finally` và đừng làm mất ngoại lệ gốc khi dọn dẹp. Với tài nguyên `AutoCloseable`, `try-with-resources` được thiết kế để giữ các lỗi phát sinh khi đóng tài nguyên trong danh sách `suppressed` ở những tình huống thích hợp.

## 7. Sự khác nhau giữa `throw` tường minh và một lời gọi luôn ném

Xét hai phiên bản:

```java
static void alwaysFails() {
    throw new IllegalStateException("failed");
}

static void caller() {
    alwaysFails();
    System.out.println("after call"); // Java vẫn xem là reachable.
}
```

Cả hai phương thức là đoạn mã đặt trong một lớp. Dù `alwaysFails()` không bao giờ trả bình thường khi được thực thi đúng như nội dung đã viết, trình biên dịch Java **không suy diễn xuyên phương thức** rằng lời gọi `alwaysFails()` chắc chắn sẽ ném. Một câu lệnh biểu thức gọi phương thức thông thường được xem là có thể hoàn tất bình thường theo quy tắc reachability của JLS §14.22. Vì thế dòng `after call` được chấp nhận lúc biên dịch, dù không được chạy trong kịch bản này.

So sánh:

```java
static void directThrow() {
    throw new IllegalStateException("failed");
    // System.out.println("after throw"); // Nếu mở comment, không biên dịch.
}
```

Ở `directThrow()`, trình biên dịch nhìn thấy trực tiếp `throw` ngay trước câu lệnh kế tiếp. Điều này chứng minh **“không thể chạy theo thực tế” khác với “unreachable theo đặc tả”**.

Tương tự, trình biên dịch không phân tích sâu mọi giá trị của biến để chứng minh đường đi có/không có thể xảy ra. Hãy sử dụng cấu trúc điều khiển rõ ràng, thay vì dựa vào giả định rằng trình biên dịch sẽ suy ra ý nghĩa nghiệp vụ của phương thức tên `alwaysFails` hoặc một hằng cục bộ.

## 8. Ngoại lệ đặc biệt: `if (true)` và quy tắc reachability của Java

Quy tắc `if` có một trường hợp đặc biệt theo JLS §14.22. Dù điều kiện là hằng `true` và nhánh chứa `throw` vô điều kiện, câu lệnh **sau toàn bộ `if` không có `else`** vẫn có thể được coi là reachable. Ví dụ này biên dịch được:

```java
class IfReachabilityExample {
    static void example() {
        if (true) {
            throw new IllegalStateException("always");
        }
        System.out.println("reachable to compiler");
    }
}
```

Ở runtime, nếu gọi `example()` thì ngoại lệ luôn xảy ra, nên dòng in không chạy. Nhưng JLS cố ý có quy tắc đặc biệt cho `if` để hỗ trợ các kiểu viết mã mang tính “biên dịch có điều kiện”. Vì vậy, **đừng kết luận rằng `javac` phải báo unreachable cho mọi mã mà con người chứng minh được không chạy**.

Trái lại, bên **trong cùng khối của nhánh `if`**, đặt câu lệnh sau `throw` vô điều kiện vẫn là unreachable:

```java
class IfInvalidExample {
    static void example() {
        if (true) {
            throw new IllegalStateException("always");
            // System.out.println("never"); // Nếu mở comment: unreachable.
        }
    }
}
```

Còn `if-else` là một cấu trúc khác: để toàn bộ `if-else` có thể hoàn tất bình thường thì ít nhất một nhánh phải có thể hoàn tất bình thường. Nếu cả hai nhánh đều chứa `throw` vô điều kiện, mã sau cấu trúc sẽ bị từ chối là unreachable.

**Lưu ý:** quy tắc đặc biệt này không cho phép đặt một câu lệnh trực tiếp sau `throw` trong cùng khối; nó liên quan đến khả năng hoàn tất bình thường được mô hình hóa cho **toàn bộ câu lệnh `if`**.

## 9. `switch`, `case` và các nhánh sau `throw`

Một `case` khác không nhất thiết bị chặn bởi câu lệnh `throw` ở `case` trước. `switch` có nhiều điểm vào theo nhãn `case`.

Ví dụ hợp lệ:

```java
static void choose(int code) {
    switch (code) {
        case 1:
            throw new IllegalArgumentException("bad code");
        case 2:
            System.out.println("code=2");
            break;
        default:
            System.out.println("other");
    }
    System.out.println("switch done");
}
```

Đây là phương thức minh họa, không phải tệp độc lập. Với `code = 2`, chương trình vào `case 2` trực tiếp; không cần đi qua `case 1` và lệnh `throw` ở đó. Với `code = 1`, ngoại lệ được ném. Với giá trị khác `1` và `2`, `default` chạy. Câu lệnh sau `switch` là reachable vì có những đường hoàn tất bình thường.

Trong `switch expression`, nhánh `throw` cũng có thể hợp lệ vì nó biểu diễn một trường hợp lỗi trong khi các nhánh khác trả về giá trị:

```java
static int points(int level) {
    return switch (level) {
        case 1 -> 10;
        case 2 -> 20;
        default -> throw new IllegalArgumentException("unknown level");
    };
}
```

Nhánh `default` ném ngoại lệ, nhưng hai nhánh khác giúp biểu thức `switch` cung cấp kết quả bình thường. Không cần viết một biểu thức “giá trị giả” phía sau `throw`. Đoạn mã dùng cú pháp `switch expression` ổn định trong Java 17.

## 10. Vòng lặp và câu lệnh không thể tới: liên hệ nhưng không đồng nhất

JLS còn có quy tắc về vòng lặp không thể hoàn tất bình thường, ví dụ `while (true)` không có `break` phù hợp:

```java
class InvalidInfiniteLoop {
    static void loop() {
        while (true) {
            System.out.println("running");
        }
        // System.out.println("after loop"); // Nếu mở comment: unreachable.
    }
}
```

Đây là ví dụ về reachability **không cần `throw`**. Nó cho thấy lỗi unreachable đến từ mô hình luồng điều khiển của ngôn ngữ nói chung. Nếu vòng lặp có `break` reachable thoát khỏi nó, câu lệnh sau vòng lặp có thể reachable.

Điểm khác biệt đáng chú ý:

```java
static void conditionalLoop(boolean keepRunning) {
    while (keepRunning) {
        if (Thread.currentThread().isInterrupted()) {
            break;
        }
    }
    System.out.println("after loop");
}
```

Phương thức này chỉ minh họa đường điều khiển, **không nên dùng làm vòng chờ quay liên tục trong chương trình thực tế**. Khi điều kiện `keepRunning` là biến (không phải hằng `true`), về mặt reachability có thể thoát vòng lặp để tới `after loop`. Quy tắc reachability không dự đoán vòng lặp chắc chắn chấm dứt trong một lần chạy cụ thể.

Khi so sánh với `throw`: một câu lệnh `throw` vô điều kiện không thể hoàn tất bình thường trong mọi trường hợp, còn cấu trúc vòng lặp hoặc `if` phải được xét theo quy tắc riêng của chúng.

## 11. Lỗi thường gặp và cách sửa

| Sai lầm | Vì sao sai | Cách sửa phù hợp |
| --- | --- | --- |
| Đặt `System.out.println` hoặc `return` sau `throw` vô điều kiện trong cùng khối. | Lệnh trước không thể hoàn tất bình thường nên lệnh tiếp theo unreachable. | Bỏ dòng vô hiệu, hoặc chuyển hành động cần chạy lên **trước `throw`**. |
| Viết `throw ...; return defaultValue;` để bảo đảm có giá trị trả về. | `throw` đã là đường kết thúc phương thức; `return` phía sau unreachable. | Dùng `throw` cho đường lỗi và `return` riêng cho đường hợp lệ. |
| Cho rằng `catch` sẽ chạy rồi quay lại dòng ngay sau `throw`. | `catch` xử lý và tiếp tục sau cấu trúc `try` thích hợp, không quay lại điểm ném. | Chuyển mã cần chạy sau xử lý sang **sau `try-catch`**. |
| Nhầm checked exception error với `unreachable statement`. | Đây là hai bước kiểm tra độc lập. | Với checked exception, `catch` hoặc khai báo `throws` đúng kiểu. |
| Nhầm lời gọi “luôn ném” với `throw` trực tiếp. | Trình biên dịch không suy diễn xuyên phương thức theo cách đó. | Diễn đạt kiểm tra và nhánh lỗi tại vị trí hợp lý; không trông chờ `javac` phân tích toàn chương trình. |
| Diễn giải `if (true) { throw ... }` như mã buộc phải gây unreachable ở ngoài `if`. | `if` có quy tắc reachability đặc biệt. | Áp dụng đúng quy tắc JLS cho cấu trúc bao quanh. |
| Đặt `return` trong `finally` để “dọn dẹp”. | `return` của `finally` có thể che khuất ngoại lệ ban đầu. | Chỉ đặt công việc dọn dẹp cần thiết trong `finally`; tránh thay thế lý do lỗi đang được truyền. |

Một thay đổi nhỏ thường rõ ràng hơn:

**Trước (không hợp lệ nếu bỏ comment):**

```java
static String requireName(String name) {
    throw new IllegalArgumentException("invalid");
    // return name.trim(); // unreachable
}
```

**Sau (hợp lệ, có hai đường đi):**

```java
static String requireName(String name) {
    if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("invalid");
    }
    return name.trim();
}
```

Nhờ có điều kiện, đoạn `return name.trim();` chạy được khi đầu vào hợp lệ; nếu đầu vào sai, phương thức ném ngoại lệ và không cố trả giá trị.

## 12. Cách tự kiểm tra luồng điều khiển

Đối với một đoạn mã có `throw`, hãy lần theo thứ tự:

1. **Xác định cấp đang xét:** ngay trong một khối, toàn bộ `if-else`, một `case` của `switch`, một vòng lặp, `try-catch-finally` hay lời gọi phương thức.
2. **Hỏi câu lệnh trước có thể hoàn tất bình thường không:** nếu là `throw` trực tiếp thì câu trả lời luôn là **không**.
3. **Xem cấu trúc bao ngoài có xử lý lý do đột ngột không:** nhánh `catch` phù hợp, `finally`, `switch` và các đường điều khiển riêng không phải cùng một kiểu xử lý.
4. **Áp dụng reachability theo JLS:** không suy ra lỗi biên dịch chỉ từ việc một dòng sẽ không được chạy với đầu vào hiện tại.
5. **Kiểm tra ngoại lệ checked riêng:** sau khi kết luận về luồng điều khiển, vẫn phải xem phương thức cần `throws` hay `catch`.

Thử dự đoán kết quả của đoạn mã sau khi đặt trong một phương thức:

```java
int value = 3;
try {
    if (value > 0) {
        throw new IllegalArgumentException("positive");
    }
    System.out.println("inside try");
} catch (IllegalArgumentException ex) {
    System.out.println("handled: " + ex.getMessage());
}
System.out.println("outside");
```

Kết quả:

```text
handled: positive
outside
```

Dòng `inside try` **reachable theo phân tích tĩnh** vì điều kiện phụ thuộc vào biểu thức sử dụng biến, nhưng không chạy trong lần thực thi này. Ngoại lệ được `catch` xử lý, nên dòng `outside` chạy. Nếu đem dòng `inside try` vào ngay sau `throw` **trong cùng khối con của nhánh `if`**, câu lệnh mới sẽ là unreachable khi biên dịch.

## 13. Tự kiểm tra nhanh

**Câu 1.** Sau `throw new RuntimeException();` vô điều kiện, có thể đặt `return 0;` trong cùng khối để tránh lỗi thiếu `return` không?  
**Không.** `throw` đã kết thúc đường chạy và `return` phía sau là unreachable.

**Câu 2.** Câu lệnh ở ngoài `try-catch` có thể chạy sau khi `throw` bên trong `try` không?  
**Có.** Nếu `catch` phù hợp xử lý lỗi và cấu trúc `try-catch` hoàn tất bình thường, luồng tiếp tục ở câu lệnh sau nó.

**Câu 3.** Nếu `throw` nằm trong một nhánh `if`, câu lệnh sau `if` luôn unreachable?  
**Không.** Phải xét cấu trúc `if` và quy tắc reachability đặc biệt của nó.

**Câu 4.** Có thể đặt `case 2:` sau `case 1: throw ...;` trong `switch` statement?  
**Có.** `case 2` là một điểm vào khác của `switch` và có thể reachable.

**Câu 5.** Nếu gọi phương thức mà thân chỉ có `throw`, trình biên dịch có bắt lỗi unreachable cho dòng phía sau lời gọi không?  
**Không chỉ vì thế.** Java không phân tích xuyên phương thức để suy ra lời gọi luôn thất bại.

**Câu 6.** `throw null;` có được xem là hoàn tất bình thường không?  
**Không.** Khi chạy, Java tạo và ném `NullPointerException`, nên câu lệnh vẫn hoàn tất đột ngột.

## Tham khảo đặc tả

- [Java Language Specification 21 — §14.18 The `throw` Statement](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.18).
- [Java Language Specification 21 — §14.22 Unreachable Statements](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.22).
- [Java Language Specification 21 — §14.20 `try` Statement](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.20).
- [Java Language Specification 21 — §11.2 Compile-Time Checking of Exceptions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-11.html#jls-11.2).

**Ghi nhớ:** `throw` luôn hoàn tất đột ngột. Một lệnh đặt trực tiếp sau `throw` vô điều kiện trong cùng khối là unreachable, nhưng một `catch` phù hợp hoặc một nhánh điều khiển khác có thể tạo đường tiếp tục hợp lệ **ở cấp cấu trúc bao ngoài**. Phải đánh giá reachability theo quy tắc Java, không chỉ theo dự đoán của một lần chạy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="throw-statement-008">Throw trong nhánh switch và luồng điều khiển có trả về giá trị</a>

<details>
<summary>Xem chi tiết</summary>

# Throw trong nhánh switch và luồng điều khiển có trả về giá trị trong Java

Trong Java, `throw` **chấm dứt bất thường đường thực thi hiện tại** bằng cách ném một đối tượng ngoại lệ. Điều này đặc biệt quan trọng khi một nhánh `switch` phải quyết định **trả về giá trị hay báo lỗi**: nhánh hợp lệ cung cấp một kết quả, còn nhánh không hợp lệ có thể dùng `throw` để ngăn chương trình tiếp tục với giá trị giả.

Từ Java 14, `switch expression` là tính năng chuẩn; Java 17+ sử dụng được cú pháp mũi tên `->`, `yield` và `throw` trong nhánh `switch` mà không cần bật preview. Cần phân biệt rõ:

- **`switch expression`:** Toàn bộ `switch` tham gia vào một biểu thức, có giá trị để gán hoặc trả về. Mỗi nhánh được chọn phải cung cấp giá trị theo quy tắc ngôn ngữ, hoặc hoàn tất bất thường, chẳng hạn bằng `throw`.
- **`switch statement`:** Toàn bộ `switch` là một câu lệnh điều khiển. Nhánh có thể thực hiện thao tác, thoát bằng `break`, hoặc trong ngữ cảnh phù hợp dùng `return`, `throw`.
- **`throw`:** Là câu lệnh ném ngoại lệ, **không phải một biểu thức tạo ra giá trị**. Trong một `switch expression`, một nhánh `throw` hợp lệ vì nhánh đó không kết thúc bình thường; Java không yêu cầu nhánh ném phải cung cấp giá trị.

Hiểu ba điểm trên giúp tránh sai lầm thường gặp khi kết hợp `return switch (...)`, `yield` và xử lý ngoại lệ.

## 1. Tình huống cơ bản: giá trị hợp lệ thì trả về, không hợp lệ thì throw

Ví dụ phương thức ánh xạ một mã thao tác thành giới hạn:

~~~java
static int quota(String mode) {
    return switch (mode) {
        case "fast" -> 12;
        case "safe" -> 6;
        default -> throw new IllegalArgumentException(
                "Chế độ không hỗ trợ: " + mode);
    };
}
~~~

Đây là **đoạn phương thức minh họa**, có thể đặt trong một lớp Java. Diễn giải:

1. `switch (mode)` được đánh giá và chọn một nhánh.
2. Với `"fast"`, biểu thức `switch` có giá trị `12`; `return` của phương thức trả về `12`.
3. Với `"safe"`, biểu thức `switch` có giá trị `6`.
4. Với chuỗi khác, câu lệnh `throw new IllegalArgumentException(...)` chạy. Cả biểu thức `switch` lẫn phương thức không trả về `int` trên đường đi đó; ngoại lệ được truyền ra cho bên gọi nếu chưa được bắt.

Điểm cốt lõi: phương thức khai báo `int` **không bắt buộc trả về một số ở đường dẫn ném ngoại lệ**. Mọi đường thực thi kết thúc bình thường phải trả đúng kiểu; đường đi `throw` kết thúc bất thường nên không cần `return 0` để “đủ kiểu”.

### Vì sao không trả một giá trị mặc định tùy tiện?

~~~java
static int quotaWrong(String mode) {
    return switch (mode) {
        case "fast" -> 12;
        case "safe" -> 6;
        default -> 0;
    };
}
~~~

Đoạn này **biên dịch được**, nhưng nếu `0` không phải quy ước hợp lệ của API, bên gọi có thể tưởng thao tác thành công và tiếp tục với dữ liệu sai. Khi đầu vào vi phạm hợp đồng, ném ngoại lệ thường rõ nghĩa hơn trả một “giá trị phép” chưa được định nghĩa.

Ngược lại, nếu nghiệp vụ quy định mọi chế độ không nhận diện đều phải có hạn mức `0` thì `default -> 0` lại là lựa chọn đúng. `throw` không phải mục tiêu tự thân: hãy chọn kết quả theo **hợp đồng của phương thức**.

## 2. Quy tắc quan trọng của `switch expression`

Xét một biểu thức gán kết quả:

~~~java
int level = switch (name) {
    case "basic" -> 1;
    case "pro" -> 2;
    default -> throw new IllegalArgumentException("Tên không hợp lệ");
};
~~~

Đây là **đoạn minh họa** cần biến `name` phù hợp trong một phương thức. Nhánh chọn `"basic"` hoặc `"pro"` hoàn tất bình thường với giá trị `int`. Nhánh `default` ném ngoại lệ và không cung cấp giá trị.

Không nên giải thích `throw` như một giá trị `int` đặc biệt. Nó là **kết thúc bất thường** của nhánh đang được thực thi; vì thế nó không gây xung đột với yêu cầu loại `int` của các nhánh trả giá trị.

### Dạng một biểu thức sau mũi tên

~~~java
case "basic" -> 1;
~~~

Phía phải của mũi tên là biểu thức `1`, cung cấp giá trị cho `switch expression`.

### Dạng một khối có nhiều lệnh và `yield`

~~~java
case "pro" -> {
    int base = 2;
    int multiplier = 3;
    yield base * multiplier;
}
~~~

Khi một nhánh của `switch expression` dùng khối `{ ... }`, `yield` cung cấp giá trị của biểu thức `switch`. Ở ví dụ này, kết quả là `6`.

### Dạng ném ngoại lệ

~~~java
default -> throw new IllegalArgumentException("Không có cấu hình");
~~~

Phía phải là **`throw` statement**; nhánh này không hoàn thành bình thường và không có giá trị `yield`.

### Dạng khối có kiểm tra rồi ném hoặc yield

~~~java
case "custom" -> {
    if (limit < 0) {
        throw new IllegalArgumentException("limit không được âm");
    }
    yield limit * 2;
}
~~~

Đây là nhánh minh họa có biến `limit` trong phạm vi. Khi `limit < 0`, nhánh ném ngoại lệ; khi hợp lệ, nhánh cung cấp giá trị bằng `yield`. Vì đường `throw` không cần sinh giá trị, đây là cách viết hợp lệ.

Trong khối nhánh của `switch expression`, **mọi đường có thể kết thúc bình thường mà thiếu `yield` là sai**. Một khối có `throw` trên một nhánh điều kiện vẫn cần `yield` hoặc ném ngoại lệ trên các đường còn lại.

## 3. Phân biệt `yield`, `return` và `throw`

Ba lệnh đều ảnh hưởng luồng điều khiển nhưng mục đích khác nhau.

| Lệnh | Điều xảy ra | Thường dùng ở đâu? |
| --- | --- | --- |
| `yield value;` | Hoàn tất nhánh của `switch expression`, cung cấp giá trị cho chính biểu thức `switch` | Khối nhánh `switch expression` |
| `return value;` | Kết thúc phương thức đang chạy bình thường và trả giá trị cho bên gọi | Thân phương thức, hoặc khối của `switch statement` khi thích hợp |
| `throw exception;` | Kết thúc bất thường bằng cách ném đối tượng ngoại lệ | Trong phương thức, trong nhánh `switch expression` hoặc `switch statement` |

Ví dụ hợp lệ với `yield`:

~~~java
static int calculate(String key) {
    int result = switch (key) {
        case "double" -> {
            int source = 7;
            yield source * 2;
        }
        case "one" -> 1;
        default -> throw new IllegalArgumentException("Key sai: " + key);
    };
    return result;
}
~~~

Phương thức trên là **đoạn hoàn chỉnh về logic** nhưng cần được đặt trong một lớp Java. `yield 14;` chỉ chuyển giá trị `14` ra khỏi `switch expression` để gán cho `result`; lệnh `return result;` mới trả giá trị từ phương thức `calculate`.

### Không dùng `return` bên trong khối của `switch expression`

Ví dụ **sai, không biên dịch**:

~~~java
static int invalid(String key) {
    return switch (key) {
        case "one" -> {
            return 1;  // Không được return từ bên trong switch expression.
        }
        default -> 0;
    };
}
~~~

Ở `switch expression`, khối nhánh phải dùng `yield` để đưa ra giá trị hoặc `throw` để kết thúc bất thường. Sửa thành:

~~~java
static int valid(String key) {
    return switch (key) {
        case "one" -> {
            yield 1;
        }
        default -> 0;
    };
}
~~~

### Không coi `throw` là một toán hạng thông thường

Cú pháp sau **sai** trong Java:

~~~java
int value = flag ? 10 : throw new IllegalArgumentException("Sai");
~~~

`throw` là câu lệnh, không phải biểu thức dùng được ở vị trí toán hạng thứ ba của `?:`. Có thể viết:

~~~java
int value = switch (flag) {
    case true -> 10;
    case false -> throw new IllegalArgumentException("Sai");
};
~~~

**Tuy nhiên, đoạn `switch (flag)` trên cũng không hợp lệ trong Java 17:** selector của `switch` không thể là kiểu nguyên thủy `boolean` (Java 17 chưa hỗ trợ `switch` trên `boolean` như ví dụ đó). Để đúng Java 17, hãy dùng selector `int` hoặc `String`:

~~~java
int value = switch (flag ? 1 : 0) {
    case 1 -> 10;
    default -> throw new IllegalArgumentException("Sai");
};
~~~

Các đoạn này là **minh họa cú pháp**, biến `flag` là một `boolean` có sẵn. Việc trình bày cả hai cách không hợp lệ làm rõ hai giới hạn độc lập: `throw` không phải biểu thức dùng trong `?:`, và `boolean` không phải selector `switch` tiêu chuẩn trong Java 17.

## 4. `switch statement`: nhánh throw khác nhánh return

`switch statement` là một **câu lệnh**, không trả ra một giá trị cho toán tử gán. Tuy nhiên phương thức bao quanh vẫn có thể trả giá trị từ bên trong các khối nhánh:

~~~java
static int quotaStatement(String mode) {
    switch (mode) {
        case "fast" -> {
            return 12;
        }
        case "safe" -> {
            return 6;
        }
        default -> throw new IllegalArgumentException(
                "Chế độ không hỗ trợ: " + mode);
    }
}
~~~

Đây là phương thức minh họa hợp lệ trong Java 17+. Khi `mode` hợp lệ, khối nhánh gọi `return` để **trả khỏi phương thức**; khi không hợp lệ, `throw` làm phương thức kết thúc bất thường.

Điểm khác biệt so với `return switch (...)`:

- Với **`switch expression`**, các nhánh sinh giá trị `-> 12`, `-> 6`; phương thức có một `return switch (...)` bao quanh.
- Với **`switch statement`**, các nhánh dùng `return` trong khối để trả khỏi phương thức.
- Cả hai có thể dùng nhánh `default -> throw ...` để không trả giá trị giả.

### Vì sao phải có dấu ngoặc quanh `return` ở nhánh mũi tên của switch statement?

Một `switch rule` sau `->` có phần thân thuộc một trong các loại mà cú pháp cho phép, như biểu thức dạng câu lệnh, khối hoặc `throw` statement. Đoạn sau không phải cách viết hợp lệ:

~~~java
switch (mode) {
    case "fast" -> return 12;  // Sai: return không phải phần thân trực tiếp ở đây.
    default -> throw new IllegalArgumentException();
}
~~~

Sửa bằng khối:

~~~java
switch (mode) {
    case "fast" -> {
        return 12;
    }
    default -> throw new IllegalArgumentException();
}
~~~

Hai đoạn trên cần nằm trong một phương thức trả `int` và có biến `mode` phù hợp. Với `switch expression`, `case "fast" -> 12;` lại là cách đúng để trả **giá trị biểu thức**.

### Dạng switch statement cổ điển dùng dấu hai chấm

Java 17 vẫn hỗ trợ cú pháp `case ...:` và lệnh `break`:

~~~java
static int quotaClassic(String mode) {
    switch (mode) {
        case "fast":
            return 12;
        case "safe":
            return 6;
        default:
            throw new IllegalArgumentException(
                    "Chế độ không hỗ trợ: " + mode);
    }
}
~~~

Phương thức này hợp lệ. `return` kết thúc cả phương thức và `throw` kết thúc bất thường, nên không cần `break` ở các nhánh đó. Nhưng nếu một nhánh cổ điển chỉ chạy vài câu lệnh bình thường mà không `break`, `return` hoặc `throw`, chương trình có thể **rơi tiếp (fall-through)** sang nhánh kế tiếp; cú pháp mũi tên `->` giúp tránh kiểu rơi tiếp không chủ ý đó.

## 5. Tính bao phủ (exhaustiveness) và nhánh ném ngoại lệ

**`switch expression` phải bao phủ mọi giá trị selector có thể xảy ra theo quy tắc biên dịch.** Cách phổ biến với `String` hoặc `int` là khai báo `default`. `default` có thể cung cấp giá trị hoặc ném ngoại lệ.

~~~java
static String stateLabel(int state) {
    return switch (state) {
        case 0 -> "READY";
        case 1 -> "RUNNING";
        default -> throw new IllegalArgumentException(
                "Mã trạng thái không biết: " + state);
    };
}
~~~

Ở đây, các giá trị `0` và `1` cho ra chuỗi; mọi giá trị khác ném ngoại lệ. Nhánh `throw` **được tính là một nhánh bao phủ hợp lệ** dù không tạo ra `String`.

Nếu bỏ `default` trong `switch expression` cho `int` mà không còn cách bao phủ toàn bộ các giá trị, trình biên dịch sẽ báo lỗi vì biểu thức `switch` không exhaustive. Đặt `default -> throw ...` là cách biểu đạt rõ ràng rằng giá trị ngoài miền hợp lệ **không được chấp nhận**.

### Trường hợp enum: bao phủ đầy đủ bằng các hằng số

~~~java
enum Stage { NEW, ACTIVE, CLOSED }

static int priority(Stage stage) {
    return switch (stage) {
        case NEW -> 1;
        case ACTIVE -> 2;
        case CLOSED -> throw new IllegalStateException(
                "Giai đoạn đóng không có độ ưu tiên");
    };
}
~~~

Hai khai báo trên có thể đặt trong một lớp Java. Với `enum Stage` đang có đúng ba hằng số, ba nhánh đã bao phủ toàn bộ các hằng số, vì vậy không bắt buộc `default`. Nhánh `CLOSED` dùng `throw` vẫn đủ để tạo một `switch expression` hợp lệ.

Khi thêm hằng số mới vào `enum` rồi biên dịch lại mã sử dụng, `switch expression` không có `default` thường phải được cập nhật để vẫn exhaustiveness. Đây có thể là chủ đích tốt: trình biên dịch nhắc người phát triển xét từng trạng thái mới.

Đừng coi `default -> throw` luôn cần thiết trong mọi `switch expression`. Với enum có đầy đủ case, bỏ `default` thường giúp phát hiện sự thay đổi miền giá trị khi build; chọn cách nào phải dựa trên hợp đồng API.

### Một nhánh ném không làm toàn bộ switch vô giá trị

~~~java
int x = switch (code) {
    case 1 -> 10;
    case 2 -> 20;
    default -> throw new IllegalArgumentException("code sai");
};
~~~

Những nhánh hoàn tất bình thường đều là giá trị `int`; nhánh `throw` không cần “đóng góp một kiểu” cho việc tạo kết quả. Tuy vậy nếu các nhánh còn lại trả kiểu không tương thích với ngữ cảnh đích, trình biên dịch vẫn báo lỗi loại dữ liệu.

## 6. Checked exception trong nhánh `switch`

`throw` trong `switch` chịu **chính quy tắc checked/unchecked** như ở mọi nơi khác. `throw new IOException(...)` là ném checked exception; phương thức bao quanh phải bắt hoặc khai báo. `throw new IllegalArgumentException(...)` là unchecked, không bắt buộc khai báo `throws`.

Ví dụ checked:

~~~java
static int loadQuota(String mode) throws java.io.IOException {
    return switch (mode) {
        case "standard" -> 5;
        case "offline" -> throw new java.io.IOException(
                "Không thể tải hạn mức khi ngoại tuyến");
        default -> throw new IllegalArgumentException(
                "Chế độ không hợp lệ: " + mode);
    };
}
~~~

Phương thức này biên dịch hợp lệ trong Java 17+ vì đã khai báo `throws IOException`. Khi `mode` là `"offline"`, không có `int` nào được trả về. Bên gọi phải tuân thủ hợp đồng checked của `IOException`.

### Không khai báo checked exception: lỗi biên dịch

Ví dụ **sai**:

~~~java
static int invalidChecked(String mode) {
    return switch (mode) {
        case "offline" -> throw new java.io.IOException("Mất kết nối");
        default -> 5;
    };
}
~~~

Trình biên dịch yêu cầu bắt hoặc khai báo `IOException`. Sửa bằng `throws java.io.IOException` trong chữ ký hoặc xử lý qua `try/catch` phù hợp tại tầng có khả năng quyết định.

Việc nhánh `offline` chỉ chạy khi selector khớp không tự miễn yêu cầu checked tại thời điểm biên dịch. Ngược lại, checked exception **không bắt buộc phương thức trả một số đặc biệt trong nhánh ném**.

## 7. Ví dụ Java 17+ hoàn chỉnh: trả kết quả, yield và throw

Chương trình sau kiểm tra trực tiếp ba cách sử dụng:

1. `switch expression` trả `int` bằng nhánh biểu thức hoặc `yield`.
2. `switch expression` ném checked hoặc unchecked exception theo nhánh.
3. `switch statement` dùng `return` trong khối nhánh và `throw` ở `default`.

Lưu toàn bộ chương trình trong tệp **`SwitchThrowDemo.java`**:

~~~java
public class SwitchThrowDemo {
    static class QuotaUnavailableException extends Exception {
        QuotaUnavailableException(String message) {
            super(message);
        }
    }

    public static void main(String[] args) {
        for (String mode :
                new String[] {"fast", "safe", "blocked", "unknown"}) {
            try {
                System.out.println(mode + " => " + quota(mode));
            } catch (QuotaUnavailableException e) {
                System.out.println(
                        mode + " => checked: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                System.out.println(
                        mode + " => unchecked: " + e.getMessage());
            }
        }

        System.out.println(
                "statement safe => " + quotaStatement("safe"));

        try {
            quotaStatement("wrong");
        } catch (IllegalArgumentException e) {
            System.out.println(
                    "statement wrong => " + e.getMessage());
        }
    }

    static int quota(String mode)
            throws QuotaUnavailableException {
        return switch (mode) {
            case "fast" -> 12;
            case "safe" -> {
                int base = 4;
                yield base + 2;
            }
            case "blocked" -> throw new QuotaUnavailableException(
                    "Hạn mức tạm khóa");
            default -> throw new IllegalArgumentException(
                    "Chế độ lạ: " + mode);
        };
    }

    static int quotaStatement(String mode) {
        switch (mode) {
            case "fast" -> {
                return 12;
            }
            case "safe" -> {
                return 6;
            }
            default -> throw new IllegalArgumentException(
                    "Không hỗ trợ: " + mode);
        }
    }
}
~~~

Biên dịch và chạy (JDK 17 trở lên):

~~~bash
javac SwitchThrowDemo.java
java SwitchThrowDemo
~~~

**Stdout chính xác:**

~~~text
fast => 12
safe => 6
blocked => checked: Hạn mức tạm khóa
unknown => unchecked: Chế độ lạ: unknown
statement safe => 6
statement wrong => Không hỗ trợ: wrong
~~~

### Lần theo từng nhánh

- **`fast`:** Nhánh `case "fast" -> 12;` hoàn tất `switch expression` với `12`; `return` của phương thức `quota` chuyển giá trị đó về `main`.
- **`safe`:** Nhánh khối khai báo `base = 4` rồi `yield base + 2;`, cung cấp giá trị `6` cho biểu thức `switch`; phương thức trả `6`.
- **`blocked`:** Nhánh `case "blocked" -> throw new QuotaUnavailableException(...)` tạo và ném checked exception. Vì `quota` khai báo `throws QuotaUnavailableException`, `main` xử lý bằng `catch (QuotaUnavailableException e)`. Không có giá trị `int` được trả về trên đường đi này.
- **`unknown`:** Nhánh `default` ném `IllegalArgumentException`, được khối `catch` unchecked tương ứng bắt.
- **`quotaStatement("safe")`:** `switch statement` không tự là một biểu thức trả giá trị; `return 6;` trong khối nhánh thoát khỏi phương thức.
- **`quotaStatement("wrong")`:** `default -> throw ...` khiến phương thức kết thúc bất thường, không in một dòng kết quả thành công; thông báo ở `main` được tạo bởi `catch`.

Trong chương trình, hai khối `catch` nằm cạnh nhau không tranh nhau nhận cùng một loại: `QuotaUnavailableException` là checked kế thừa `Exception`; `IllegalArgumentException` là unchecked kế thừa `RuntimeException`. Đầu ra không có dòng stack trace vì mọi ngoại lệ do ví dụ phát sinh đều được bắt ở `main`.

## 8. Luồng điều khiển và tính “mọi đường đều trả về giá trị”

Phương thức có kiểu trả về khác `void` phải thỏa các quy tắc của Java về khả năng hoàn tất bình thường và trả về giá trị. Có thể mô hình hóa một phương thức như sau:

~~~text
return switch (mode)
    |
    +-- nhánh hợp lệ --> sinh giá trị --> return cho caller
    |
    +-- nhánh bị cấm --> throw ngoại lệ --> tìm catch phù hợp
                                              |
                                              +-- caller bắt và xử lý
                                              +-- hoặc ngoại lệ tiếp tục lan truyền
~~~

Điểm quan trọng là đường `throw` không đến câu lệnh `return` nhưng vẫn hợp lệ: phương thức không hoàn tất bình thường trên đường đó.

### Các trường hợp cần phân biệt

| Trường hợp | Hành vi |
| --- | --- |
| Nhánh `-> 10` của `switch expression` | Cung cấp giá trị `10` cho toàn biểu thức |
| Nhánh `-> { yield 10; }` | Cung cấp giá trị `10` cho toàn biểu thức |
| Nhánh `-> throw new RuntimeException()` | Không tạo giá trị, kết thúc bất thường |
| Nhánh `-> { throw new RuntimeException(); }` | Không tạo giá trị, kết thúc bất thường |
| Khối `switch statement` chứa `return 10;` | Trả về `10` từ phương thức |
| Khối `switch statement` chứa `throw ...;` | Kết thúc bất thường và tìm handler |
| Khối `switch statement` chỉ in log rồi kết thúc | Tiếp tục sau `switch statement` nếu có câu lệnh tiếp theo |

Ví dụ cuối cùng rất dễ gây nhầm:

~~~java
static int value(String mode) {
    switch (mode) {
        case "fast" -> System.out.println("fast");
        default -> System.out.println("other");
    }
    return 1;
}
~~~

Đây là một phương thức **hợp lệ**: `switch` chỉ in thông báo, sau đó `return 1;` mới trả về kết quả cho tất cả đầu vào. Đừng diễn giải `System.out.println(...)` trong `switch statement` như một nhánh tạo giá trị giống `switch expression`.

### Vì sao code này sai?

~~~java
static int broken(String mode) {
    switch (mode) {
        case "fast" -> {
            return 12;
        }
        case "safe" -> {
            System.out.println("safe");
        }
        default -> throw new IllegalArgumentException("mode sai");
    }
}
~~~

Đoạn minh họa này **không biên dịch**: nếu `mode` là `"safe"`, phương thức có thể hoàn tất bình thường mà không trả `int`. Đường `default -> throw` chỉ xử lý đầu vào khác; nó không cung cấp `return` cho đường `safe`. Muốn sửa, hãy trả kết quả ở nhánh `safe` hoặc đặt một `return` chung sau `switch statement` với hợp đồng thích hợp.

Đối với `switch expression`, một nhánh khối kết thúc bình thường mà không `yield` sẽ bị từ chối theo quy tắc riêng của biểu thức `switch`.

## 9. Tổ chức xử lý ngoại lệ khi dùng `return switch`

Tùy ý nghĩa của API, bạn có thể để ngoại lệ lan lên caller hoặc bắt ở ranh giới phù hợp:

~~~java
static int quotaOrDefault(String mode, int fallback) {
    try {
        return switch (mode) {
            case "fast" -> 12;
            case "safe" -> 6;
            default -> throw new IllegalArgumentException(
                    "Chế độ không hợp lệ");
        };
    } catch (IllegalArgumentException e) {
        return fallback;
    }
}
~~~

Phương thức minh họa trên **trả fallback** cho đầu vào không được hỗ trợ. Điều này chỉ hợp lý nếu hợp đồng thực sự cho phép dùng fallback. Nếu dùng sai chế độ là lỗi bắt buộc báo cho caller, nên để ngoại lệ lan truyền.

Đừng viết `catch (Exception e)` bao quanh mọi `switch` rồi trả giá trị cố định một cách im lặng: cách đó có thể che cả lỗi logic hoặc trạng thái mà ứng dụng phải điều tra.

### Vai trò của `finally` khi một nhánh throw hoặc return

~~~java
static int example(boolean fail) {
    try {
        return switch (fail ? "fail" : "ok") {
            case "ok" -> 9;
            default -> throw new IllegalStateException("không thành công");
        };
    } finally {
        System.out.println("cleanup");
    }
}
~~~

Đây là phương thức minh họa hợp lệ. Trong điều kiện chạy bình thường:

- `example(false)` in `cleanup` rồi trả `9`.
- `example(true)` in `cleanup`, sau đó ngoại lệ `IllegalStateException` tiếp tục lan ra ngoài.

`finally` thường chạy khi rời `try`, cả khi nhánh đã tạo giá trị `return` và khi nhánh ném ngoại lệ. Không nên đặt `return` hoặc ném ngoại lệ khác vô ý trong `finally` vì có thể che kết quả hoặc lỗi đang truyền. Các tình huống chấm dứt JVM bất thường có thể ngăn `finally` chạy.

## 10. `switch`, null và kiểu selector trong Java 17

Cú pháp nhánh `throw` không thay đổi các giới hạn kiểu của `switch` trong Java 17.

### Selector `null`

Nếu selector tham chiếu như `String mode` là `null`, `switch (mode)` trong Java 17 theo tính năng tiêu chuẩn sẽ ném `NullPointerException` trước khi chọn nhánh; `default` **không** phải nhánh tự động xử lý `null`.

~~~java
static int level(String name) {
    return switch (name) {
        case "low" -> 1;
        default -> throw new IllegalArgumentException("Tên không hợp lệ");
    };
}
~~~

Với `level(null)`, Java 17 thường ném `NullPointerException` tại đánh giá selector. Muốn lỗi theo hợp đồng của riêng mình, có thể kiểm tra trước:

~~~java
static int levelChecked(String name) {
    if (name == null) {
        throw new IllegalArgumentException("name không được null");
    }
    return switch (name) {
        case "low" -> 1;
        default -> throw new IllegalArgumentException("Tên không hợp lệ");
    };
}
~~~

Hai phương thức chỉ minh họa xử lý đầu vào. Không dùng `case null` hoặc pattern matching for switch như thể đó là cú pháp Java 17 chuẩn không cần preview; các tính năng pattern switch thay đổi theo phiên bản Java.

### Kiểu `boolean` trong switch Java 17

Java 17 tiêu chuẩn hỗ trợ `switch` trên một số kiểu nguyên thủy nguyên, các kiểu wrapper liên quan, `String` và `enum`; **không hỗ trợ selector `boolean`** theo cú pháp chuẩn Java 17. Vì thế:

~~~java
int x = switch (flag) {
    case true -> 1;
    default -> throw new IllegalArgumentException();
};
~~~

là **phản ví dụ không biên dịch với Java 17**, ngay cả khi ý định dùng `throw` hoàn toàn rõ. Nếu chỉ có điều kiện boolean, hãy dùng `if/else`:

~~~java
static int requireEnabled(boolean enabled) {
    if (!enabled) {
        throw new IllegalStateException("Chưa bật");
    }
    return 1;
}
~~~

Đây là phương thức hoàn chỉnh về logic, có thể đặt trong một lớp Java. Không cần ép mọi điều kiện đơn giản thành `switch`.

## 11. Các lỗi thường gặp

**Lỗi 1: Nghĩ rằng `throw` tạo ra một giá trị mặc định cho `switch expression`.**  
Sai. `throw` không cung cấp giá trị; nó kết thúc bất thường. Khi được bắt ở caller, caller xử lý ngoại lệ theo hợp đồng, chứ không nhận một `int` hay `String` giả.

**Lỗi 2: Dùng `return` trong khối của `switch expression`.**  
Khối nhánh phải `yield value`, `throw`, hoặc hoàn tất bất thường theo cách hợp lệ. `return` ở bên trong `switch expression` là lỗi biên dịch; `return switch (...)` ở bên ngoài biểu thức mới trả về từ phương thức.

**Lỗi 3: Thiếu `yield` trên một đường có thể kết thúc bình thường.**  
Nếu nhánh khối có điều kiện ném, nhánh còn lại phải cung cấp giá trị; không thể chỉ in log rồi kết thúc khối khi toàn bộ `switch` cần một giá trị.

**Lỗi 4: Viết `case X -> return value;` trong `switch statement`.**  
Cú pháp đúng với `return` là `case X -> { return value; }`. Nếu là `switch expression`, chỉ cần `case X -> value;` hoặc khối `yield`.

**Lỗi 5: Quên tính exhaustive của `switch expression`.**  
Đối với selector như `int` hoặc `String`, dùng `default` nếu cần bao phủ các giá trị không liệt kê. `default -> throw ...` là một cách xử lý hợp lệ khi đầu vào ngoài miền nên bị từ chối.

**Lỗi 6: Ném checked exception mà không bắt hoặc khai báo.**  
`case "offline" -> throw new IOException(...)` vẫn tuân thủ luật checked exception như mọi câu lệnh `throw` khác.

**Lỗi 7: Cho rằng `default` luôn xử lý `null`.**  
Với selector `String` là `null` trong Java 17 không dùng tính năng preview, một `NullPointerException` thường xuất hiện trước khi nhánh `default` chạy. Kiểm tra `null` trước nếu API muốn lỗi rõ hơn.

**Lỗi 8: Trả giá trị giả chỉ để ép mọi đường có giá trị.**  
Nếu đầu vào không hợp lệ, `default -> throw new IllegalArgumentException(...)` thường phản ánh hợp đồng tốt hơn `default -> 0` không có ngữ nghĩa.

**Lỗi 9: Giả định switch statement luôn phải trả về một giá trị.**  
`switch statement` có thể chỉ chạy các thao tác rồi tiếp tục sau cấu trúc. Nhưng nếu chính phương thức khai báo trả giá trị, nó vẫn phải thỏa quy tắc `return` trên mọi đường hoàn tất bình thường.

**Lỗi 10: Cố gắng đặt `throw` như một toán hạng biểu thức bất kỳ.**  
`throw` là statement. Cú pháp `case ... -> throw ...` được ngôn ngữ hỗ trợ riêng, không có nghĩa `throw` được phép xuất hiện thay mọi biểu thức trong `?:` hay ở toán hạng của phép cộng.

## 12. Bài tập tự kiểm tra

1. Vì sao phương thức `int quota(String mode)` vẫn biên dịch khi nhánh `default` chỉ `throw new IllegalArgumentException(...)` mà không trả `int`?
2. Trong `return switch (...)`, khi nào viết `case X -> 10;`, khi nào viết `case X -> { yield 10; }`?
3. Điều gì sai với `case X -> { return 10; }` khi nó nằm trong một `switch expression`? Cách viết đúng là gì?
4. Với `switch statement`, vì sao `case X -> { return 10; }` có thể hợp lệ trong phương thức trả `int`?
5. Nếu một nhánh `switch expression` ném `IOException`, phương thức bao quanh phải đáp ứng yêu cầu gì?
6. Vì sao `default -> throw ...` có thể làm biểu thức `switch` trên `int` đáp ứng exhaustiveness?
7. Trong Java 17, gọi phương thức sử dụng `switch (someString)` với selector `null` có chắc vào `default` không?
8. Khi `return switch (...)` nằm trong `try/finally`, `finally` có thường chạy trước khi phương thức thật sự trả giá trị hoặc ném lỗi ra ngoài không?
9. Có thể dùng `throw new RuntimeException(...)` làm nhánh thứ ba của toán tử `?:` như một biểu thức Java thông thường không?
10. Vì sao phiên bản `switch statement` chỉ ghi log ở một nhánh rồi đi tiếp có thể gây lỗi “missing return statement” cho một phương thức trả `int`?
11. Với `enum` đã được liệt kê đầy đủ trong `switch expression`, có bắt buộc có `default` không? Điểm hữu ích của việc bỏ `default` khi phát triển enum là gì?
12. Nếu `catch` quanh `return switch` bắt `IllegalArgumentException` rồi trả `fallback`, điều gì cần được xác định trong hợp đồng API trước khi chọn cách này?

### Gợi ý đáp án

1. Đường ném ngoại lệ không hoàn tất bình thường, nên không cần sinh `int`.
2. Dạng mũi tên ngắn khi chỉ có một biểu thức; khối `yield` khi cần thực hiện thêm lệnh.
3. `return` bị cấm trong khối của `switch expression`; đổi thành `yield 10;`.
4. Đây là `switch statement`; `return` trong khối hợp lệ và kết thúc phương thức.
5. Bắt hoặc khai báo checked `IOException`.
6. Vì xử lý tất cả selector còn lại bằng kết thúc bất thường cũng hợp lệ.
7. Không; selector `null` thường gây `NullPointerException` trong Java 17 tiêu chuẩn.
8. Có, khi luồng thoát phạm vi `try` bình thường; tránh viết code `finally` làm che lỗi hoặc kết quả.
9. Không, `throw` là câu lệnh chứ không phải toán hạng biểu thức thông thường.
10. Vì đường đó hoàn tất bình thường mà không trả giá trị cho phương thức.
11. Không nếu đã bao phủ enum; bỏ `default` có thể giúp compiler phát hiện case cần bổ sung khi enum thay đổi và biên dịch lại.
12. Xác định liệu đầu vào không hợp lệ được phép chuyển sang giá trị dự phòng hay phải báo lỗi cho caller.

## 13. Ghi nhớ

Trong một **`switch expression`**, các nhánh hợp lệ tạo giá trị bằng biểu thức hoặc `yield`; nhánh từ chối có thể dùng **`throw`** và không phải tạo ra giá trị. Trong một **`switch statement`**, khối nhánh có thể trực tiếp **`return`** khỏi phương thức hoặc **`throw`** để báo lỗi. Cả hai kiểu đều tuân thủ checked exception, quy tắc xử lý ngoại lệ và luồng điều khiển của Java. Khi thiết kế mã, hãy đặt câu hỏi: *nhánh này cần cung cấp kết quả hợp lệ hay phải chấm dứt bất thường vì đầu vào/trạng thái không hợp lệ?*

</details>

- [Quay lại đầu trang](#back-to-top)
