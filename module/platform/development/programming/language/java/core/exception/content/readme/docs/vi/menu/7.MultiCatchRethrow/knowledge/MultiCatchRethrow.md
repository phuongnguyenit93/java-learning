<a id="back-to-top"></a>

# Multi-catch và rethrow

## Menu
- [Multi-catch từ Java 7 và cú pháp dấu |](#multi-catch-rethrow-001)
- [Khi nào nhiều loại ngoại lệ có thể dùng chung một cách xử lý](#multi-catch-rethrow-002)
- [Không đặt kiểu cha và kiểu con trong cùng multi-catch](#multi-catch-rethrow-003)
- [Tham số multi-catch ngầm định final](#multi-catch-rethrow-004)
- [Giới hạn thành viên được truy cập qua tham số multi-catch](#multi-catch-rethrow-005)
- [Precise rethrow và suy ra tập checked exception hẹp hơn](#multi-catch-rethrow-006)
- [Ảnh hưởng của final, effectively final và việc gán lại tham số catch](#multi-catch-rethrow-007)
- [So sánh multi-catch với catch kiểu cha quá rộng](#multi-catch-rethrow-008)

## <a id="multi-catch-rethrow-001">Multi-catch từ Java 7 và cú pháp dấu |</a>

<details>
<summary>Xem chi tiết</summary>

# Multi-catch từ Java 7 và cú pháp dấu | trong Java

> Java Core – Exception · Nhóm: Multi-catch và rethrow · Trọng tâm: cú pháp từ Java 7, các alternative hợp lệ, kiểu tham số catch và cách dùng trong xử lý ngoại lệ. Ví dụ chính chạy với Java 17+.

## 1. Multi-catch dùng để làm gì?

Một tác vụ có thể gặp nhiều loại lỗi nhưng cần **cùng một cách xử lý**. Trước Java 7, cách thông thường là viết nhiều nhánh `catch` và lặp lại code:

```java
try {
    loadData();
} catch (IOException ex) {
    System.out.println("Cannot process data: " + ex.getMessage());
} catch (NumberFormatException ex) {
    System.out.println("Cannot process data: " + ex.getMessage());
}
```

Từ **Java 7**, multi-catch cho phép gom các kiểu ngoại lệ vào **một tham số catch**, phân tách bằng dấu gạch đứng `|`:

```java
try {
    loadData();
} catch (IOException | NumberFormatException ex) {
    System.out.println("Cannot process data: " + ex.getMessage());
}
```

Hai đoạn trên là **mã minh họa**, giả định có import `java.io.IOException` và hàm `loadData()` được khai báo thích hợp. Chúng chưa phải chương trình độc lập.

**Ý nghĩa:** Khi thân `try` ném một đối tượng exception phù hợp với **bất kỳ** kiểu nào được liệt kê, Java chỉ chạy **một handler chung**. Cú pháp này giảm code trùng, thể hiện rằng các lỗi có cùng chính sách xử lý và giữ nguyên đối tượng exception gốc.

### Kết quả học tập

Bạn sẽ biết cách:

1. Đọc và viết `catch (A | B ex)` đúng cú pháp Java 7+.
2. Xác định khi nào hai kiểu được ghép và khi nào compiler cấm vì có quan hệ subtype.
3. Phân biệt multi-catch với nhiều catch độc lập, hiểu kiểu tham số được suy luận và quy tắc ngầm `final`.
4. Bắt cả checked và unchecked exception trong cùng một handler khi có cùng chính sách.
5. Dùng `throw ex;` để truyền tiếp exception và hiểu liên hệ với precise rethrow từ Java 7.
6. Biên dịch chương trình Java 17, so sánh chính xác handler được chọn và stdout.

## 2. Cú pháp dấu | trong tham số catch

Cấu trúc:

```java
try {
    operation();
} catch (FirstException | SecondException ex) {
    handle(ex);
}
```

Đoạn này minh họa ngữ pháp; các tên kiểu/hàm chỉ là ký hiệu, chưa phải mã có thể biên dịch độc lập.

| Thành phần | Ý nghĩa |
| --- | --- |
| `try { ... }` | Khối thực thi có thể ném exception |
| `FirstException` | Alternative thứ nhất |
| `|` | Dấu ngăn cách các **kiểu ngoại lệ** trong union type |
| `SecondException` | Alternative thứ hai |
| `ex` | **Một biến tham số**, tham chiếu đối tượng exception thực tế |
| `{ handle(ex); }` | **Một khối handler** dùng chung cho các alternative |

Dấu `|` không phải `||`. Trong multi-catch, dấu này nằm **giữa những tên kiểu** và không thực hiện phép OR boolean hay bitwise OR trong lúc xử lý đối tượng.

Ví dụ **hợp lệ về mặt quan hệ các alternative**:

```java
catch (IOException | NumberFormatException | IllegalStateException ex) {
    System.out.println(ex.getClass().getSimpleName());
}
```

Ba loại không có quan hệ subtype với nhau. Khi đặt vào chương trình thật, vẫn phải đáp ứng các kiểm tra của compiler về checked exception và thứ tự handler.

Ngược lại, đây là ví dụ **cố ý sai cú pháp**:

```java
// Sai: không dùng || hoặc dấu phẩy phân cách kiểu:
catch (IOException || NumberFormatException ex) { }
catch (IOException, NumberFormatException ex) { }
```

Ngoài ra, union type ở đây dành cho khai báo tham số catch; không có cú pháp biến thông thường như `IOException | NumberFormatException ex;`.

**Mốc phiên bản:** Multi-catch được đưa vào Java SE 7 qua các cải tiến ngôn ngữ của Project Coin (JSR 334). Nó không yêu cầu thư viện ngoài. Nếu phải biên dịch theo cú pháp Java 6, tính năng này chưa tồn tại; nếu phải hỗ trợ Java 7 trở lên, cú pháp đã hợp lệ.

## 3. Quy tắc subtype: không được ghép lớp cha và lớp con

Đây là ràng buộc quan trọng nhất. Theo [JLS 17 mục 14.20](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20), trong một union có nhiều alternative, **không được có một alternative là subtype của alternative khác**.

Quan sát cây kế thừa:

```text
java.lang.Throwable
└── java.lang.Exception
    ├── java.io.IOException
    │   └── java.io.FileNotFoundException
    └── java.lang.RuntimeException
        ├── java.lang.IllegalArgumentException
        │   └── java.lang.NumberFormatException
        └── java.lang.IllegalStateException
```

### 3.1. Hai kiểu độc lập: hợp lệ

```java
catch (IOException | NumberFormatException ex) {
    System.out.println(ex.getMessage());
}
```

Một `IOException` không phải `NumberFormatException`, và ngược lại. Ngoại lệ thực tế là kiểu nào thì biến `ex` tham chiếu chính đối tượng đó.

### 3.2. Cha và con: không hợp lệ

```java
// Sai, chỉ minh họa lỗi biên dịch:
catch (IOException | FileNotFoundException ex) {
    System.out.println(ex.getMessage());
}
```

`FileNotFoundException` là subtype của `IOException`. Union chồng lấp khiến compiler từ chối. Đảo thành `FileNotFoundException | IOException` **vẫn sai**, vì mối quan hệ kế thừa không thay đổi.

Tương tự:

```java
// Sai: NumberFormatException extends IllegalArgumentException.
catch (NumberFormatException | IllegalArgumentException ex) {
    System.out.println(ex.getMessage());
}
```

Nếu hai loại cha–con **cần xử lý như nhau**, chỉ cần catch **kiểu cha**:

```java
catch (IOException ex) {
    System.out.println("I/O failure");
}
```

Nếu phải xử lý **khác nhau**, dùng hai catch theo thứ tự con trước cha:

```java
try {
    readFile();
} catch (FileNotFoundException ex) {
    System.out.println("Missing file");
} catch (IOException ex) {
    System.out.println("Other I/O error");
}
```

Đoạn trên giả định hàm `readFile()` có khả năng ném IOException, chỉ để minh họa thứ tự catch.

**So sánh then chốt:** Ở **multi-catch**, hai alternative cha–con bị cấm **trong mọi thứ tự**. Ở **nhiều catch riêng**, có thể khai báo cả hai nếu **lớp con đứng trước lớp cha** để handler của lớp con không bị che.

## 4. Khi thực thi, multi-catch bắt một đối tượng exception

Ví dụ:

```java
try {
    readThenParse();
} catch (IOException | NumberFormatException ex) {
    System.out.println(ex.getClass().getSimpleName());
}
```

Một lần ném sẽ truyền đi **một instance Throwable**, không tạo hai instance. Java kiểm tra các handler theo thứ tự và tìm catch đầu tiên phù hợp; trong union, chỉ cần **một alternative** phù hợp với kiểu runtime.

Giả sử hàm phát sinh `FileNotFoundException`:

- `FileNotFoundException` là subtype của `IOException`, nên alternative `IOException` bắt được.
- Biến `ex` giữ đối tượng `FileNotFoundException` ban đầu.
- `ex.getClass().getSimpleName()` cho kết quả `FileNotFoundException`, không phải `IOException` hay tên của union.
- Khối xử lý chạy **một lần**, các sibling catch còn lại của cùng try không được chạy.
- Nếu khối handler ném exception mới, các sibling catch không tự bắt exception mới phát sinh trong handler đó.

Multi-catch không phải cách gom nhiều lỗi đồng thời. Nếu lỗi đóng tài nguyên xuất hiện thêm khi một ngoại lệ chính đã ném trong `try-with-resources`, cơ chế có thể dùng `getSuppressed()` để bảo toàn lỗi phụ; đó là vấn đề khác với ký hiệu `|`.

## 5. Ví dụ Java 17 hoàn chỉnh và kết quả chuẩn

Lưu mã dưới đây thành **`MultiCatchDemo.java`**. Ví dụ dùng cú pháp `switch ->` của Java hiện đại nên cần **Java 17+**; riêng cú pháp multi-catch bên trong đã tồn tại từ Java 7.

```java
import java.io.FileNotFoundException;
import java.io.IOException;

public class MultiCatchDemo {
    private static void perform(String mode) throws IOException {
        switch (mode) {
            case "missing" -> throw new FileNotFoundException("config.txt");
            case "io" -> throw new IOException("read failed");
            case "number" -> Integer.parseInt("NaN");
            case "argument" -> throw new IllegalArgumentException("unsupported input");
            case "ok" -> System.out.println("work=ok");
            default -> throw new IllegalStateException("unexpected mode");
        }
    }

    private static void run(String mode) {
        System.out.println("== " + mode + " ==");
        try {
            perform(mode);
            System.out.println("try completed");
        } catch (IOException | NumberFormatException ex) {
            System.out.println("group=" + ex.getClass().getSimpleName());
        } catch (IllegalArgumentException ex) {
            System.out.println("argument=" + ex.getMessage());
        } finally {
            System.out.println("finally");
        }
        System.out.println("after");
    }

    private static void rethrowFileError() throws IOException {
        try {
            throw new FileNotFoundException("log.txt");
        } catch (IOException | IllegalArgumentException ex) {
            System.out.println("inside=" + ex.getClass().getSimpleName());
            throw ex;
        }
    }

    public static void main(String[] args) {
        run("missing");
        run("io");
        run("number");
        run("argument");
        run("ok");

        System.out.println("== rethrow ==");
        try {
            rethrowFileError();
        } catch (IOException ex) {
            System.out.println("outside=" + ex.getClass().getSimpleName());
        }
    }
}
```

Với JDK hỗ trợ `--release 17`:

```bash
javac --release 17 -encoding UTF-8 MultiCatchDemo.java
java MultiCatchDemo
```

**Stdout chính xác** đã được đối chiếu bằng cách biên dịch và chạy chương trình:

```text
== missing ==
group=FileNotFoundException
finally
after
== io ==
group=IOException
finally
after
== number ==
group=NumberFormatException
finally
after
== argument ==
argument=unsupported input
finally
after
== ok ==
work=ok
try completed
finally
after
== rethrow ==
inside=FileNotFoundException
outside=FileNotFoundException
```

### Trường hợp missing

`perform("missing")` ném `FileNotFoundException`. Nó được bắt bởi alternative `IOException` vì thuộc cây kế thừa IOException. Handler nhóm in `group=FileNotFoundException` theo kiểu **runtime** thực tế, sau đó khối `finally` in `finally` rồi in `after`.

### Trường hợp io

`perform("io")` ném đối tượng `IOException`. Nó được bắt trực tiếp bởi alternative đầu tiên, in `group=IOException`. Không có handler nào khác của cấu trúc try chạy.

### Trường hợp number

`Integer.parseInt("NaN")` ném `NumberFormatException` do không phải dạng số nguyên hợp lệ. Alternative thứ hai bắt ngoại lệ này và chạy **cùng khối** với IOException. Điều đó chứng minh multi-catch có thể nhóm **checked** và **unchecked exception** khi chính sách xử lý giống nhau.

### Trường hợp argument

`perform("argument")` ném `IllegalArgumentException` thông thường. Nó là kiểu **cha** của `NumberFormatException`, nhưng không phải mọi IllegalArgumentException đều là NumberFormatException. Union phía trước chỉ bắt NumberFormatException, nên lỗi được chuyển sang `catch (IllegalArgumentException)` sau đó.

Một catch cho lớp cha có thể bắt instance lớp con, nhưng **không có chiều ngược lại**.

### Trường hợp ok

`perform("ok")` in `work=ok` và hoàn thành bình thường; thân try in tiếp `try completed`. Các catch không chạy. `finally` vẫn chạy, sau đó in `after`.

### Trường hợp rethrow

`rethrowFileError()` ném một `FileNotFoundException`, bắt nó ở union `IOException | IllegalArgumentException`, in `inside=FileNotFoundException` rồi `throw ex;`. Caller ở `main` nhận **cùng exception** qua `catch (IOException)`, in `outside=FileNotFoundException`.

`throw ex;` không tự tạo exception mới khi `ex` là tham chiếu hợp lệ đến đối tượng đã bắt.

## 6. Kiểu của tham số ex và quy tắc implicitly final

Trong multi-catch:

```java
catch (IOException | NumberFormatException ex) {
    System.out.println(ex.getMessage());
    System.out.println(ex.getCause());
}
```

tham số `ex` có **một kiểu khai báo** được compiler suy ra từ các alternative. Theo JLS, kiểu đó là **least upper bound**: `lub(D1, D2, ...)`. Nó có thể là một supertype hoặc một kiểu có ràng buộc chung, chứ không tùy theo từng lần chạy mà đổi kiểu tĩnh.

Do đó các thành viên **chung** của `Throwable` như `getMessage()`, `getCause()`, `getSuppressed()`, `printStackTrace()` và `getClass()` đều có thể dùng. Một phương thức chỉ có ở một subtype không được gọi trực tiếp trên `ex` nếu kiểu khai báo chung không cung cấp phương thức đó.

### Không được gán lại ex

Biến exception của multi-catch được **ngầm khai báo final** (*implicitly final*), nên đoạn sau bị compiler từ chối:

```java
try {
    readThenParse();
} catch (IOException | NumberFormatException ex) {
    // Sai nếu bỏ comment:
    // ex = new IOException("another failure");
}
```

Dù không viết từ khóa `final`, bạn không thể đổi `ex` sang một exception khác hoặc gán `null` vào nó.

So sánh: trong một **uni-catch** như `catch (IOException ex)`, tham số không được ngầm final. Nó có thể là *effectively final* nếu không bị gán lại. Dù cú pháp đơn catch cho phép gán lại trong một số trường hợp, việc đó thường không nên làm vì khiến khó theo dõi exception gốc và có thể ảnh hưởng đến phân tích rethrow.

**Không nhầm hai thuật ngữ:** Tham số multi-catch là **implicitly final**; tham số uni-catch chưa gán lại có thể là **effectively final**. JLS phân biệt hai trường hợp nhưng cả hai có vai trò trong việc bảo toàn kiểu exception khi rethrow.

## 7. Multi-catch và precise rethrow từ Java 7

Cùng với multi-catch, Java 7 cải tiến cách kiểm tra checked exception khi **ném lại chính tham số `catch`** bằng `throw ex;`. Trong chương trình `MultiCatchDemo` ở phần 5, `rethrowFileError()` bắt `IOException | IllegalArgumentException` rồi ném lại `ex`. Caller vẫn phân biệt được lỗi `FileNotFoundException` thực tế, và chữ ký `throws IOException` là đủ; không cần mở rộng thành `throws Exception`.

Đây là hai tính năng riêng: **multi-catch** gom các loại lỗi có chung handler; **precise rethrow** cho phép compiler suy luận tập checked exception có thể thoát khỏi `try` khi biến `catch` là `final` hoặc *effectively final*. Một `catch (Exception ex)` đơn, không gán lại `ex`, cũng có thể dùng precise rethrow. Nếu sửa tham chiếu `ex` trong single-catch, lợi ích suy luận ấy có thể mất.

Ở bước nhập môn, hãy kiểm tra ba điều: ngoại lệ thực có được giữ nguyên khi `throw ex` không, chữ ký `throws` có bao quát checked exception còn thoát ra không, và handler có phục hồi hay chỉ chuyển trách nhiệm lên tầng gọi. Phân tích chính xác tập ngoại lệ và ảnh hưởng của phép gán lại sẽ được đào sâu ở phần học về rethrow.

## 8. Checked, unchecked và các catch nằm sau union

`IOException` là checked: compiler yêu cầu bắt hoặc khai báo nếu có thể phát sinh. `NumberFormatException` là unchecked nên có thể không khai báo `throws`, nhưng vẫn được phép bắt. Multi-catch không biến checked thành unchecked.

Để một catch hợp lệ, ngoài điều kiện subtype, compiler còn kiểm tra **liệu checked exception có thể phát sinh trong try** theo quy tắc JLS. Ví dụ chỉ parse một chuỗi số và không gọi API I/O mà lại có catch `IOException` thì thường bị báo không thể bắt kiểu checked không phát sinh.

### Catch phía sau có thể bị che

Ví dụ **cố ý không biên dịch**, giả định `readFile()` có thể ném IOException:

```java
try {
    readFile();
} catch (IOException | NumberFormatException ex) {
    System.out.println("group");
} catch (FileNotFoundException ex) {
    System.out.println("missing");
}
```

`FileNotFoundException` đã được alternative IOException của catch đầu bao phủ. Do đó catch sau không thể nhận exception mà catch đầu không bắt, khiến compiler từ chối. Đây là lỗi **thứ tự catch**; khác với lỗi **alternative có subtype với nhau trong cùng union**.

Cách tổ chức hợp lệ nếu muốn phân biệt tệp thiếu và lỗi I/O khác:

```java
try {
    readFile();
} catch (FileNotFoundException | NumberFormatException ex) {
    System.out.println("known failure");
} catch (IOException ex) {
    System.out.println("other I/O failure");
}
```

Hai alternative đầu không liên hệ cha–con, và catch IOException phía sau vẫn có thể nhận những IOException khác FileNotFoundException.

## 9. Khi nào dùng multi-catch và khi nào dùng catch riêng?

### Dùng multi-catch khi cùng một chính sách

Ví dụ chương trình đọc cấu hình rồi parse số. Nếu lỗi I/O và lỗi định dạng số cùng có nghĩa “cấu hình không sử dụng được”, có thể chuyển hai loại sang một ngoại lệ tầng ứng dụng:

```java
try {
    loadConfiguration();
} catch (IOException | NumberFormatException ex) {
    throw new IllegalStateException("Invalid configuration", ex);
}
```

Đây là đoạn minh họa, giả định hàm `loadConfiguration()` tồn tại. Constructor `IllegalStateException(String, Throwable)` giữ nguyên exception gốc ở `cause` cho việc chẩn đoán.

### Dùng catch riêng khi hướng khôi phục khác nhau

```java
try {
    loadConfiguration();
} catch (IOException ex) {
    retryLater(ex);
} catch (NumberFormatException ex) {
    askUserToCorrectInput(ex);
}
```

Đoạn này chỉ minh họa các hàm nghiệp vụ. Lỗi I/O có thể retry sau khi kiểm tra điều kiện; lỗi định dạng số cần sửa input. Việc ép cả hai vào một catch nhóm sẽ làm mất ý nghĩa nghiệp vụ.

### Không gom Exception hoặc Throwable theo thói quen

`catch (Exception)` có thể bắt cả lỗi logic unchecked như `NullPointerException`, dễ khiến ứng dụng tiếp tục ở trạng thái không hợp lệ. `catch (Throwable)` còn bao gồm cả `Error`, thường không thích hợp để “nuốt” lỗi rồi chạy tiếp. Dùng kiểu bắt đúng khả năng phục hồi của tầng hiện tại.

Multi-catch giúp loại bỏ code lặp, nhưng không phải lý do để bỏ qua các khác biệt quan trọng về xử lý lỗi.

## 10. Chương trình tối giản tương thích cú pháp Java 7

Ví dụ chính ở phần 5 dùng switch hiện đại để trình bày nhiều tình huống. Để xác nhận **riêng tính năng multi-catch từ Java 7**, có thể dùng chương trình sau với cú pháp cổ điển.

Lưu tên tệp **`Java7MultiCatch.java`**:

```java
import java.io.IOException;

public class Java7MultiCatch {
    static void work(int kind) throws IOException {
        if (kind == 1) {
            throw new IOException("io");
        }
        throw new NumberFormatException("number");
    }

    public static void main(String[] args) {
        try {
            work(1);
        } catch (IOException | NumberFormatException ex) {
            System.out.println(ex.getClass().getSimpleName());
        }
    }
}
```

Nếu dùng JDK 7 thật:

```bash
javac Java7MultiCatch.java
java Java7MultiCatch
```

Stdout:

```text
IOException
```

Nếu thay `work(1)` bằng `work(2)`, output thành `NumberFormatException`. Hai lần chạy đều vào một catch nhóm nhưng vẫn bảo toàn **kiểu runtime**.

Không nên mặc định mọi compiler JDK mới còn hỗ trợ `--release 7`; dùng `javac --help` để xem mức hỗ trợ của compiler đang có. Bản Java 7 chạy trực tiếp với JDK 7 hoặc toolchain hỗ trợ release 7; ví dụ Java 17 ở phần 5 đã được kiểm chứng riêng bằng `javac --release 17`.

## 11. Bảng lỗi cú pháp và quy tắc thường gặp

| Cách viết | Kết luận | Giải thích |
| --- | --- | --- |
| `catch (IOException | NumberFormatException ex)` | Hợp lệ nếu try phù hợp | Hai kiểu không chồng subtype |
| `catch (IOException|NumberFormatException ex)` | Hợp lệ | Không bắt buộc có khoảng trắng quanh dấu gạch đứng |
| `catch (IOException || NumberFormatException ex)` | Sai | Dùng nhầm toán tử boolean `||` |
| `catch (IOException, NumberFormatException ex)` | Sai | Dấu phẩy không phân tách union |
| `catch (IOException | FileNotFoundException ex)` | Sai | Kiểu cha và con chồng nhau |
| `catch (IllegalArgumentException | NumberFormatException ex)` | Sai | NumberFormatException là subtype |
| `catch (IOException | IOException ex)` | Sai | Alternative lặp đúng cùng kiểu |
| `catch (IOException | NumberFormatException ex) { ex = null; }` | Sai | Tham số multi-catch ngầm final |
| `catch (IOException ex)` | Hợp lệ nếu try phù hợp | Uni-catch bình thường |
| `catch (IOException | IllegalStateException ex)` | Hợp lệ nếu try phù hợp | Checked và unchecked có thể nằm chung |

Các đoạn đánh dấu “Sai” dùng để minh họa lỗi biên dịch, **không đưa vào chương trình runnable**.

## 12. Quy trình kiểm thử multi-catch

Với một đoạn code mới, hãy kiểm tra theo trình tự:

1. **Xác định tập lỗi thực tế:** đọc chữ ký `throws` của API để biết checked exception; thêm những unchecked exception có ý nghĩa nghiệp vụ.
2. **Vẽ quan hệ `extends`:** đặc biệt kiểm tra cặp `FileNotFoundException/IOException`, `NumberFormatException/IllegalArgumentException` và các subtype khác.
3. **Chỉ gom lỗi có cùng chính sách:** nếu hướng phục hồi khác nhau, dùng catch riêng.
4. **Đặt multi-catch trong thứ tự handler hợp lệ:** subtype được xử lý trước supertype ở các catch nối tiếp.
5. **Không gán lại biến `ex`:** nó ngầm final trong multi-catch.
6. **Giữ cause khi bọc lại:** ví dụ constructor `new IllegalStateException(message, ex)`.
7. **Kiểm thử mỗi alternative:** một ca phát sinh alternative thứ nhất, một ca phát sinh alternative thứ hai, một ca không lỗi và một ca chỉ phù hợp với catch khác.
8. **Nếu rethrow, kiểm tra chữ ký và caller:** checked exception có thể cần `throws` tương ứng; tránh nuốt lỗi.
9. **Kiểm tra loại exception, không khóa cứng message hệ thống:** message I/O có thể khác môi trường và bản JDK.

### Tình huống bài tập

Giả sử `read()` có thể ném IOException:

```java
try {
    read();
} catch (FileNotFoundException | NumberFormatException ex) {
    System.out.println("A");
} catch (IOException ex) {
    System.out.println("B");
}
```

Đây là đoạn minh họa đặt trong method có khai báo `read()`, chưa phải chương trình độc lập.

| Exception từ try | Output | Lý do |
| --- | --- | --- |
| `FileNotFoundException` | `A` | Khớp alternative FileNotFoundException |
| `NumberFormatException` | `A` | Khớp alternative NumberFormatException |
| IOException khác FileNotFoundException | `B` | Không khớp union đầu, khớp catch IOException |
| Không có exception | Không in A/B | Các catch không chạy |

Nếu đổi union đầu thành `IOException | FileNotFoundException`, compiler từ chối vì chồng subtype. Nếu đưa `catch (IOException)` lên trước union chứa `FileNotFoundException`, compiler từ chối vì nhánh nhóm bị che đối với loại FileNotFoundException.

## 13. Những hiểu nhầm cần tránh

- **“Dấu | làm cả hai handler chạy.”** Không: đó là một tham số, một khối xử lý và một đối tượng exception được ném.
- **“Kiểu cha và con hợp lệ nếu đảo thứ tự trong cùng union.”** Không: subtype bị cấm trong union ở mọi thứ tự.
- **“Multi-catch chỉ bắt checked exception.”** Không: có thể dùng cả checked và unchecked.
- **“ex đổi kiểu tĩnh theo từng alternative lúc chạy.”** Không: compiler xác định một kiểu khai báo chung; object runtime vẫn giữ kiểu cụ thể.
- **“Có thể gán ex sang exception khác.”** Không: tham số multi-catch ngầm final.
- **“Bắt NumberFormatException là bắt được mọi IllegalArgumentException.”** Không: một catch kiểu con không tự nhận mọi instance kiểu cha.
- **“Multi-catch tự thay thế throws.”** Không: checked exception vẫn được phân tích tại compile time.
- **“Một exception từ catch đầu sẽ được catch tiếp bởi sibling catch.”** Không: sibling catch của cùng try chỉ xử lý exception từ thân try.
- **“throw ex tạo exception mới.”** Không: với ex khác null, đây là ném lại cùng đối tượng.
- **“Multi-catch và precise rethrow là một tính năng.”** Không: chúng có mục đích khác nhau dù cùng xuất hiện từ Java 7.
- **“Càng gom nhiều kiểu càng tốt.”** Không: chỉ nên gom loại có chính sách xử lý giống nhau.
- **“Bắt exception là đã cleanup tài nguyên.”** Không: cleanup vẫn cần cấu trúc như `try-with-resources` hoặc `finally` thích hợp.

## 14. Câu hỏi tự kiểm tra

**1.** Multi-catch xuất hiện từ Java mấy và dấu phân tách alternative là gì?

*Đáp án:* Java 7; dùng một dấu `|` ở giữa các kiểu.

**2.** Tại sao `IOException | FileNotFoundException` không biên dịch?

*Đáp án:* Vì FileNotFoundException là subtype của IOException.

**3.** Ném FileNotFoundException có được catch bởi `IOException | NumberFormatException` không?

*Đáp án:* Có, thông qua alternative IOException.

**4.** Nếu `catch (IOException | NumberFormatException ex)` đã bắt NumberFormatException, catch IllegalArgumentException phía sau có chạy nữa không?

*Đáp án:* Không. Chỉ handler đầu tiên phù hợp được thực thi.

**5.** Tại sao một IllegalArgumentException trực tiếp không vào nhóm `IOException | NumberFormatException`?

*Đáp án:* Vì NumberFormatException là lớp con của IllegalArgumentException, không phải mọi instance IllegalArgumentException đều là NumberFormatException.

**6.** Có thể gán lại `ex` trong multi-catch không?

*Đáp án:* Không, nó ngầm final.

**7.** `throw ex;` trong catch nhóm có tạo IOException mới hay tự động bỏ yêu cầu checked exception không?

*Đáp án:* Không. Nó ném lại object hiện có; compiler vẫn phân tích checked exception có thể thoát ra và caller cần xử lý/khai báo phù hợp.

**8.** Nếu IOException cần retry còn NumberFormatException cần yêu cầu nhập lại, nên dùng multi-catch không?

*Đáp án:* Nên cân nhắc tách hai catch vì chính sách phục hồi khác nhau.

## 15. Tài liệu chính thức

- [Java Language Specification 17 – §14.20 The try statement](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20): cú pháp multi-catch, union alternatives, ràng buộc subtype, kiểu tham số và ngầm final.
- [JLS 17 – §14.20.1 Execution of try-catch](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.1): nguyên tắc handler phù hợp đầu tiên.
- [JLS 17 – §11.2.2 Exception Analysis of Statements](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.2.2): cơ chế precise rethrow trong phân tích exception.
- [JLS 17 – §11.2.3 Exception Checking](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.2.3): quy tắc checked exception.
- [Oracle tutorial – Catching Multiple Exception Types](https://docs.oracle.com/javase/tutorial/essential/exceptions/catch.html): diễn giải multi-catch và cách dùng thực tế.
- [JSR 334 – Small Enhancements to the Java Programming Language](https://jcp.org/en/jsr/detail?id=334): nhóm cải tiến ngôn ngữ trong Java 7.

**Ghi nhớ:** Cú pháp `catch (A | B ex)` gom các lỗi có cùng cách xử lý bằng **một handler**. Các alternative phải không có quan hệ subtype với nhau; `ex` được ngầm khai báo final; thứ tự catch khác vẫn có ý nghĩa; và khi ném lại, checked exception vẫn được compiler kiểm tra theo quy tắc rethrow của Java.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multi-catch-rethrow-002">Khi nào nhiều loại ngoại lệ có thể dùng chung một cách xử lý</a>

<details>
<summary>Xem chi tiết</summary>

# Khi nào nhiều loại ngoại lệ có thể dùng chung một cách xử lý trong Java?

Trong Java, không phải mỗi loại ngoại lệ đều cần một khối `catch` riêng. Nếu nhiều loại lỗi dẫn đến **cùng quyết định xử lý**, Java cho phép gom chúng trong một nhánh *multi-catch*: `catch (IOException | SQLException ex)`. Tuy nhiên, việc có thể viết chung về **cú pháp** không có nghĩa việc xử lý chung luôn đúng về **nghiệp vụ**.

Ví dụ, ứng dụng đọc dữ liệu từ tệp hoặc cơ sở dữ liệu. Nếu cả hai lỗi đều cho phép sử dụng một bản dữ liệu cache an toàn, một nhánh xử lý chung có thể hợp lý. Ngược lại, nếu lỗi I/O cần thử lại, lỗi SQL cần rollback giao dịch, hoặc `InterruptedException` phải bảo toàn tín hiệu hủy, việc gộp chúng vào một nhánh chung có thể che giấu các quyết định quan trọng.

Bài học này tập trung vào tiêu chí quyết định **khi nào nên dùng chung cách xử lý**, cách viết `multi-catch`, tương tác với kế thừa, `rethrow`, xử lý ngoại lệ ở nhiều tầng và cách không làm mất nguyên nhân gốc.

## 1. Multi-catch giải quyết vấn đề gì?

Trước Java 7, khi hai ngoại lệ cần cùng hành động, mã thường phải lặp logic xử lý:

```java
try {
    loadData();
} catch (IOException ex) {
    reportFailure(ex);
} catch (SQLException ex) {
    reportFailure(ex);
}
```

Đây là đoạn minh họa trong một phương thức, giả sử `loadData()` có thể ném `IOException` hoặc `SQLException`, và `reportFailure(Exception)` đã được định nghĩa. Hai nhánh khác nhau về **kiểu ngoại lệ** nhưng cùng gọi một hàm với cùng chính sách.

Có thể viết rõ hơn bằng multi-catch:

```java
try {
    loadData();
} catch (IOException | SQLException ex) {
    reportFailure(ex);
}
```

Cả hai loại được liệt kê bằng toán tử `|` trong **một** tham số của **một** nhánh `catch`. Nếu `try` ném một trong hai kiểu tương thích, khối chung chạy đúng một lần. Mã vẫn chịu các quy tắc checked exception và khả năng đến được của `catch` như thông thường.

Mục tiêu chính là **xóa sự lặp lại của cùng một quyết định**, thay vì chỉ gom những ngoại lệ có tên hoặc nguồn phát sinh tương tự.

## 2. Tiêu chí quan trọng: các lỗi có thật sự dùng được cùng chính sách?

Hãy trả lời đủ năm câu hỏi trước khi gộp:

1. **Kết quả mong muốn có giống nhau?** Ví dụ cả hai lỗi đều dẫn đến “không thể nạp dữ liệu, dùng cache đã được kiểm chứng”, hoặc “dừng thao tác và ném một ngoại lệ nghiệp vụ có cause”.
2. **Có cùng mức độ ảnh hưởng và khả năng phục hồi?** Việc tiếp tục với kết quả dự phòng phải an toàn đối với cả hai trường hợp; không được dùng chung fallback cho một lỗi có thể làm dữ liệu không nhất quán.
3. **Có cần thao tác chuyên biệt?** Ví dụ lỗi DB có thể cần rollback hoặc đánh dấu một kết nối không còn dùng được; lỗi mạng có thể cần retry; tín hiệu interrupt cần chính sách hủy. Nếu hành động khác nhau, tách nhánh.
4. **Có cần cung cấp kết quả khác nhau cho bên gọi?** Nếu một lỗi được ánh xạ thành “không tìm thấy dữ liệu”, còn lỗi kia thành “hệ thống tạm thời không sẵn sàng”, gộp mất sự phân biệt có ích.
5. **Có bảo toàn đủ thông tin để điều tra?** Một nhánh chung vẫn cần giữ kiểu lỗi thực tế, `message`, `cause` và `suppressed` khi cần, thay vì biến tất cả thành một chuỗi vô danh.

**Chỉ nên gộp khi cùng chính sách xử lý là đúng cho mọi loại lỗi trong nhánh.** Khi các loại ngoại lệ có chiến lược khác nhau, hai `catch` riêng thường sáng sủa và đúng hơn.

| Tình huống | Có nên chung `catch`? | Lý do |
| --- | --- | --- |
| Lỗi đọc tệp và lỗi truy vấn dữ liệu; cả hai có cache hợp lệ được phép sử dụng | Có thể | Cùng quyết định fallback, nhưng cần bảo đảm cache đáp ứng yêu cầu dữ liệu |
| Lỗi đọc tệp và lỗi DB đều được chuyển thành `DataLoadException` với cause | Có thể | Cùng hợp đồng lỗi của tầng dịch vụ, vẫn giữ nguyên nhân |
| `FileNotFoundException` cần hiển thị thông báo “thiếu cấu hình”, `AccessDeniedException` cần báo lỗi quyền | Nên tách | Ý nghĩa, cách khắc phục và thông điệp khác nhau |
| `SocketTimeoutException` có thể retry, `SQLException` có thể cần rollback/đánh dấu giao dịch lỗi | Thường tách | Hành động xử lý phụ thuộc nguồn lỗi và trạng thái hệ thống |
| `InterruptedException` và `IOException` | Thường tách | InterruptedException có nghĩa hủy/chuyển trạng thái interrupt, không đơn giản là lỗi I/O |
| `IOException` và `OutOfMemoryError` | Không nên gom để “xử lý mọi lỗi” | Lỗi nghiêm trọng của JVM không bảo đảm khả năng phục hồi như I/O thông thường |

Những gợi ý trong bảng phụ thuộc ngữ cảnh. Không có quy tắc rằng lỗi SQL luôn phải rollback thủ công hoặc lỗi timeout luôn được retry: quyết định dựa trên giao dịch, idempotency và hợp đồng API thực tế.

## 3. Cú pháp và giới hạn kiểu của multi-catch

Multi-catch dùng dấu `|` để liệt kê những **loại ngoại lệ đã chọn** và một thân handler chung:

```java
try {
    loadData();
} catch (IOException | SQLException ex) {
    reportFailure(ex);
}
```

Đây là đoạn minh họa cần `loadData()` và `reportFailure(...)` được định nghĩa. `IOException` và `SQLException` không có quan hệ cha–con nên có thể xuất hiện cùng nhau khi khối `try` thực sự có khả năng ném hai checked exception ấy.

Các ràng buộc chính: không ghép một lớp với chính subtype của nó (như `IOException | FileNotFoundException`); `ex` ngầm `final`; compiler chỉ cho gọi các thành viên có trên **kiểu tĩnh chung** của các alternative; và checked exception phải phù hợp với khả năng phát sinh từ `try`. Những ràng buộc này xác định **cú pháp hợp lệ**, còn câu hỏi chính của bài là liệu cả hai lỗi có **cùng chính sách nghiệp vụ** hay không.

## 4. Chương trình Java 17+: dùng chung fallback khi cả hai lỗi có cùng hậu quả

Giả sử cả nguồn tệp và DB đều chỉ là nguồn bổ sung; dịch vụ **được phép** trả dữ liệu cache đã xác minh khi không nạp được dữ liệu mới. Đây là chính sách nghiệp vụ cho ví dụ, không phải lời khuyên dùng cache bất kể dữ liệu có quan trọng hay không.

Lưu thành `SharedFallbackDemo.java`:

```java
import java.io.IOException;
import java.sql.SQLException;

public class SharedFallbackDemo {
    static String loadFresh(int mode) throws IOException, SQLException {
        if (mode == 1) {
            throw new IOException("file unavailable");
        }
        if (mode == 2) {
            throw new SQLException("database unavailable");
        }
        return "fresh-data";
    }

    static String loadWithFallback(int mode) {
        try {
            return loadFresh(mode);
        } catch (IOException | SQLException ex) {
            System.out.println("fallback after "
                    + ex.getClass().getSimpleName());
            return "verified-cache";
        }
    }

    public static void main(String[] args) {
        System.out.println("result=" + loadWithFallback(0));
        System.out.println("result=" + loadWithFallback(1));
        System.out.println("result=" + loadWithFallback(2));
    }
}
```

Biên dịch/chạy với JDK 17 trở lên:

```bash
javac -encoding UTF-8 SharedFallbackDemo.java
java SharedFallbackDemo
```

Kết quả:

```text
result=fresh-data
fallback after IOException
result=verified-cache
fallback after SQLException
result=verified-cache
```

Đường đi chính:

1. `mode = 0`: `loadFresh` hoàn tất bình thường, không chạy `catch`, trả `fresh-data`.
2. `mode = 1`: ném `IOException`, đi vào **cùng một nhánh multi-catch**, in kiểu lỗi và trả cache.
3. `mode = 2`: ném `SQLException`, vẫn đi vào nhánh chung và trả cùng fallback.
4. Phương thức `loadWithFallback` không cần `throws IOException, SQLException`, vì hai ngoại lệ checked đều được xử lý và không tiếp tục thoát khỏi phương thức.

Lợi ích nằm ở chỗ **quyết định fallback là một quy tắc thống nhất**. Nếu trong nghiệp vụ thật, lỗi SQL có thể cho biết dữ liệu đã bị ghi một phần và cache không còn đáng tin cậy, chính sách này không còn đúng: cần tách quy trình xử lý hoặc sửa hợp đồng thao tác.

## 5. Khi cùng cách xử lý là chuyển thành ngoại lệ nghiệp vụ

Một tầng lưu trữ có thể phát sinh nhiều kiểu checked exception hạ tầng, nhưng tầng dịch vụ muốn cung cấp một hợp đồng “không thể nạp thông tin”. Nếu cả hai lỗi đều có cùng ý nghĩa tại ranh giới đó, có thể **bọc chung**, giữ nguyên nhân thực tế:

```java
catch (IOException | SQLException ex) {
    throw new DataLoadException("Cannot load data", ex);
}
```

Đây là đoạn nằm trong phương thức được khai báo phù hợp với `throws DataLoadException`; `DataLoadException` phải là lớp ngoại lệ có constructor nhận `cause`. Bọc lỗi tạo **đối tượng ngoại lệ mới**, khác với rethrow cùng đối tượng.

Lưu thành `SharedTranslationDemo.java`:

```java
import java.io.IOException;
import java.sql.SQLException;

public class SharedTranslationDemo {
    static class DataLoadException extends Exception {
        DataLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static String repository(int source) throws IOException, SQLException {
        if (source == 1) {
            throw new IOException("disk read failed");
        }
        if (source == 2) {
            throw new SQLException("query failed");
        }
        return "OK";
    }

    static String service(int source) throws DataLoadException {
        try {
            return repository(source);
        } catch (IOException | SQLException ex) {
            throw new DataLoadException("Cannot load data", ex);
        }
    }

    public static void main(String[] args) {
        for (int source = 0; source < 3; source++) {
            try {
                System.out.println("value=" + service(source));
            } catch (DataLoadException ex) {
                Throwable cause = ex.getCause();
                System.out.println("service=" + ex.getMessage());
                System.out.println("cause="
                        + cause.getClass().getSimpleName()
                        + ":" + cause.getMessage());
            }
        }
    }
}
```

```bash
javac -encoding UTF-8 SharedTranslationDemo.java
java SharedTranslationDemo
```

Kết quả:

```text
value=OK
service=Cannot load data
cause=IOException:disk read failed
service=Cannot load data
cause=SQLException:query failed
```

Hai loại lỗi bên dưới được **dịch thành một checked exception của tầng dịch vụ**. `service()` khai báo `throws DataLoadException`, còn `main()` bắt đúng hợp đồng đó. Vì `DataLoadException` nhận `ex` làm cause, bên điều tra vẫn phân biệt được `IOException` với `SQLException` và xem lại stack trace ban đầu.

Không nên thay thế bằng `throw new DataLoadException(ex.getMessage(), null)` hoặc tạo một ngoại lệ mới chỉ có message: làm vậy có thể đánh mất liên kết nguyên nhân. Khi ghi log, nên truyền cả đối tượng throwable cho API logging thay vì chỉ in text message.

## 6. Khi nào nên tách các nhánh `catch`?

Multi-catch là lựa chọn tốt khi hành động giống nhau. Khi có **quy tắc khác nhau**, hai nhánh độc lập trực tiếp phản ánh thiết kế:

```java
try {
    processRequest();
} catch (java.net.SocketTimeoutException ex) {
    scheduleBoundedRetry(ex);
} catch (java.sql.SQLException ex) {
    markOperationFailed(ex);
    throw ex;
}
```

Đây là **đoạn minh họa trong một phương thức**, giả định `processRequest()` khai báo các checked exceptions liên quan, hai phương thức hỗ trợ đã được định nghĩa, và phương thức bao ngoài khai báo `throws SQLException`. Không thể coi đây là chương trình độc lập.

Nhánh timeout lựa chọn retry có giới hạn; nhánh SQL đánh dấu thao tác thất bại và để ngoại lệ tiếp tục lan lên. Hai chính sách khác nhau nên không đáng gom chỉ để giảm số dòng.

Các dấu hiệu cần tách:

- **Mức độ khẩn cấp khác nhau:** lỗi thiếu tham số đầu vào có thể phản hồi ngay, lỗi hạ tầng cần đánh dấu thất bại hệ thống.
- **Khả năng thử lại khác nhau:** retry chỉ hợp lý nếu thao tác có tính idempotent hoặc có cơ chế bảo vệ khỏi tác dụng phụ lặp.
- **Tài nguyên và giao dịch khác nhau:** một lỗi cần đóng kết nối, lỗi khác cần rollback hoặc không được phép tiếp tục.
- **Yêu cầu bảo toàn trạng thái thread:** `InterruptedException` là tín hiệu phối hợp hủy; thường nên truyền lên hoặc đặt lại cờ interrupt và kết thúc thao tác, thay vì gom với một lỗi hạ tầng thông thường.
- **Phản hồi cho người dùng khác nhau:** lỗi quyền truy cập và lỗi tạm thời không sẵn sàng không nên tự động nhận cùng thông điệp.
- **Giám sát khác nhau:** dù cuối cùng đều thất bại, nhu cầu phân loại alert/metric có thể buộc giữ kiểu rõ ràng ở ranh giới ghi nhận.

**Không nên gộp rồi viết một chuỗi lớn `if (ex instanceof A) ... else ...` chỉ để khôi phục các nhánh đã gộp.** Nếu chính sách thực sự khác nhau, các `catch` riêng thường dễ đọc và ít lỗi hơn. Việc dùng `instanceof` vẫn hợp lý nếu có một bước nhỏ cần phân loại bổ sung bên trong một chính sách chung, nhưng nên đặt câu hỏi tại sao cần gộp.

## 7. Phân biệt “hành động chung” với “thông tin chung”

Cùng dùng một câu lệnh `logger.error("failed", ex)` chưa đủ để kết luận hai ngoại lệ nên được xử lý chung. Ghi log chỉ là **một tác dụng phụ**; điều quan trọng là **hậu quả cho hệ thống và hợp đồng tầng trên**.

Giả sử hai lỗi đều được ghi log:

```java
catch (IOException ex) {
    recordFailure(ex);
    return cachedValue();
} catch (SQLException ex) {
    recordFailure(ex);
    throw ex;
}
```

Hai nhánh chia sẻ `recordFailure(ex)`, nhưng một nhánh phục hồi, nhánh còn lại truyền lỗi. Đây **không phải cùng cách xử lý tổng thể**; không nên gộp thành một `catch` rồi tùy tiện trả cache trong mọi trường hợp.

Nếu muốn giảm mã trùng, trích logic ghi nhận thành phương thức chung như ví dụ. Hãy giữ các quyết định khác nhau ở nhánh phù hợp.

Một trường hợp khác: khi tầng biên luôn chuyển nhiều ngoại lệ sang cùng mã kết quả thất bại và cùng chính sách dừng, multi-catch có thể phù hợp dù các nguyên nhân khác nhau, **miễn nguyên nhân vẫn được lưu** để phân tích. Việc cùng thông điệp gửi cho người dùng không nhất thiết đồng nghĩa cần làm mất sự khác biệt trong log và metrics.

## 8. Multi-catch và rethrow: truyền đúng đối tượng ra tầng trên

Khi tầng hiện tại chưa thể phục hồi, một handler chung vẫn có thể ghi nhận ngữ cảnh rồi **ném lại đúng đối tượng lỗi**:

```java
static void forward() throws IOException, SQLException {
    try {
        loadData();
    } catch (IOException | SQLException ex) {
        System.out.println("forward=" + ex.getClass().getSimpleName());
        throw ex;
    }
}
```

Đây là phương thức minh họa; `loadData()` phải có hợp đồng `throws IOException, SQLException`. `throw ex;` **không** biến thất bại thành thành công và không tạo wrapper mới; caller tiếp tục chịu trách nhiệm xử lý. `IOException` và `SQLException` vẫn là hai checked exception trong chữ ký.

Nếu tầng hiện tại muốn diễn đạt một lỗi nghiệp vụ chung, thay vì ném lại có thể bọc bằng một exception mới **giữ `ex` làm `cause`**. Quyết định dịch hay ném lại là quyết định về tầng trách nhiệm, không phải lý do tự động gom các loại lỗi có chính sách khác nhau. Quy tắc suy luận checked exception hẹp khi rethrow được gọi là *precise rethrow* và có điều kiện riêng trong JLS.

## 9. Multi-catch không cho gán lại biến tham số

Trong `catch (IOException | SQLException ex)`, biến `ex` **ngầm `final`**: gán `ex = new IOException("other")` là lỗi biên dịch. Điều này không cấm **ném lại** `throw ex;`, đọc stack/cause hay tạo wrapper mới có `cause` là `ex`. Đừng nhầm việc tham số không thể gán lại với việc đối tượng `Throwable` bất biến; suppressed exceptions hoặc stack trace có thể có trạng thái riêng. Khi muốn thêm ngữ cảnh, tạo ngoại lệ mới kèm cause thay vì tìm cách sửa tham chiếu của handler.

## 10. Tính hợp lệ của `catch` với checked exceptions

Trình biên dịch Java kiểm tra xem phần `try` **có thể** ném loại checked exception mà nhánh `catch` bắt hay không. Việc viết tùy ý nhiều loại checked mà không có nguồn ngoại lệ tương ứng có thể gây lỗi biên dịch.

**Cố ý không hợp lệ:**

```java
import java.io.IOException;
import java.sql.SQLException;

class InvalidUnusedAlternative {
    static void run() {
        try {
            throw new IOException("disk");
        } catch (IOException | SQLException ex) {
            System.out.println(ex.getMessage());
        }
    }
}
```

Trong `try` này, không có đường ném `SQLException` checked; `javac` có thể báo `exception SQLException is never thrown in body of corresponding try statement`. Để hợp lệ, chỉ bắt `IOException`, hoặc đảm bảo thao tác trong `try` thực sự có hợp đồng có thể ném `SQLException`.

**Không dùng `throws SQLException` trên phương thức bao ngoài để hợp thức hóa một nhánh `catch (SQLException)` mà bản thân `try` không thể ném.** Khả năng bắt checked exception được xét theo nội dung/hoạt động của `try`, không dựa vào việc khai báo tùy ý loại lỗi trên chữ ký của phương thức chứa nó.

Với unchecked `RuntimeException`, Java cho phép bắt rộng hơn ngay cả khi trong phần `try` không có câu lệnh ném tường minh. Quy tắc checked exception là một phần kiểm tra tĩnh và cần được xét riêng với việc chọn nhánh `catch` lúc runtime.

## 11. Không được dùng multi-catch để che giấu ngữ nghĩa interrupt

Một sai lầm phổ biến là coi `InterruptedException` như một lỗi tạm thời có thể ghi log rồi tiếp tục:

```java
try {
    Thread.sleep(100);
    readData();
} catch (InterruptedException | IOException ex) {
    System.out.println("failed: " + ex.getMessage());
    // Tiếp tục công việc ngay lập tức: có thể làm mất yêu cầu hủy.
}
```

Đây là đoạn minh họa trong một phương thức, giả định `readData() throws IOException`. Cú pháp multi-catch là hợp lệ, nhưng **chính sách xử lý có vấn đề**: khi `Thread.sleep` ném `InterruptedException`, trạng thái interrupt thường đã được xóa theo hợp đồng của phương thức này. Nếu chỉ in log rồi tiếp tục, chương trình có thể vô tình bỏ qua yêu cầu dừng.

Nếu tầng hiện tại cần xử lý riêng, tách rõ:

```java
try {
    Thread.sleep(100);
    readData();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    return;
} catch (IOException ex) {
    handleIoFailure(ex);
}
```

Đoạn này giả định nằm trong một phương thức `void` với `readData() throws IOException`, `handleIoFailure(IOException)` đã tồn tại, và nó không phải chương trình độc lập. Nhánh interrupt bảo toàn tín hiệu hủy, nhánh I/O xử lý theo chính sách riêng. Nếu một phương thức có thể khai báo tiếp `throws InterruptedException`, để lan truyền cũng là lựa chọn hợp lý tùy hợp đồng.

Điểm cần nhớ là **hai lỗi cùng có thể xuất hiện trong một `try` không đủ để gộp chúng**. Cần cùng quyết định cuối cùng khi một trong hai xảy ra.

## 12. Quan hệ cha/con ảnh hưởng cách nhóm ngoại lệ

Nếu các lỗi có quan hệ kế thừa, **không thể** viết một union như `catch (FileNotFoundException | IOException ex)` vì `FileNotFoundException extends IOException`. Có hai lựa chọn tùy chính sách:

```java
catch (IOException ex) {
    handleAllIoFailures(ex); // Chỉ khi mọi lỗi I/O cần chung chính sách.
}
```

hoặc hai handler riêng, **kiểu con trước kiểu cha**:

```java
catch (FileNotFoundException ex) {
    reportMissingFile(ex);
} catch (IOException ex) {
    reportOtherIoFailure(ex);
}
```

Đây là các nhánh minh họa trong `try` phù hợp, cần import và hàm xử lý của ứng dụng. Bắt `IOException` tuy bao phủ các lớp con nhưng **không có nghĩa** mọi lỗi I/O đều nên retry hoặc dùng fallback. Mức độ bao phủ của kiểu và sự phù hợp của chính sách là hai câu hỏi khác nhau.

## 13. Khác biệt giữa `catch` riêng có logic trùng và multi-catch

Nếu mã xử lý trùng nhau nhưng có thể cần mở rộng riêng sau này, có hai cách thường hợp lệ:

**Cách A — một nhánh chung:**

```java
catch (IOException | SQLException ex) {
    throw new DataLoadException("Data loading failed", ex);
}
```

**Cách B — hai nhánh riêng dùng một hàm:**

```java
catch (IOException ex) {
    throw translate(ex);
} catch (SQLException ex) {
    throw translate(ex);
}
```

Hai đoạn là **minh họa phần `catch` của một `try`**, giả định `DataLoadException` và `translate(Exception)` đã được định nghĩa với chữ ký thích hợp. Hai cách có thể có tác dụng tương đương khi chính sách thật sự giống nhau.

Multi-catch hợp lý khi **cả quyết định lẫn tác dụng** giống nhau và có khả năng giữ ổn định. Hai `catch` riêng hữu ích nếu muốn báo metric khác nhau, bổ sung dữ liệu ngữ cảnh riêng hoặc có khả năng phải đưa ra chính sách xử lý khác nhau. Không cần cố ép một cú pháp duy nhất cho tất cả tình huống.

Một nguyên tắc thực hành: nếu thân multi-catch chủ yếu gồm các nhánh phân biệt kiểu bằng `instanceof`, đó là dấu hiệu nên cân nhắc tách các `catch` tương ứng, trừ khi phần chung vẫn là phần xử lý chính.

## 14. Có thể gom ở tầng xử lý tập trung nhưng phải giữ nghĩa lỗi

Một số ứng dụng có ranh giới xử lý chung như command-line entry point, worker task boundary hoặc global API error handler. Ở đó, nhiều lỗi có thể dẫn đến một hành động chung: đánh dấu request thất bại, trả phản hồi nhất quán, kết thúc công việc và ghi nhận trạng thái.

Dù vậy, cần phân biệt:

- **Gom cách xuất lỗi:** cùng một định dạng phản hồi, cùng cách ghi log, cùng kiểm soát việc lộ thông tin cho người dùng.
- **Gom ý nghĩa lỗi:** hai lỗi được coi như nhau về khả năng retry, mất dữ liệu, tính toàn vẹn và phương án phục hồi.

Có thể gom cách xuất lỗi nhưng vẫn giữ thông tin loại lỗi trong dữ liệu nội bộ. Nếu một lỗi nên làm người dùng nhập lại tham số, còn lỗi kia là sự cố hệ thống, việc trả cùng mã lỗi hoặc cùng hướng dẫn khắc phục có thể gây hiểu lầm.

Khi một ranh giới dùng `catch (Exception ex)` tổng quát, nó còn có thể nhận nhiều unchecked exception như `NullPointerException`, `IllegalStateException`. Vì vậy, `catch (Exception)` cần chủ đích rộng hơn đáng kể so với `catch (IOException | SQLException)`. Nó cũng **không bắt nhánh `Error`**. Chỉ nên bắt rộng ở nơi có chính sách lỗi tập trung rõ ràng, không coi đó là cách che mọi lỗi để tiếp tục làm việc như thành công.

## 15. `finally`, tài nguyên và multi-catch

Nếu trong `try` một lỗi được ném rồi chọn một nhánh multi-catch, `finally` liên quan vẫn chạy theo quy tắc Java trước khi cấu trúc hoàn tất chuyển quyền điều khiển. `finally` không biến nhiều nhánh `catch` thành nhiều lần xử lý.

Với `try-with-resources`, việc tự động đóng tài nguyên xảy ra trước khi đi tới `catch` của cấu trúc đó. Nếu phần thân `try` ném lỗi và thao tác `close()` cũng ném lỗi, lỗi đóng thường được lưu trong `getSuppressed()` của ngoại lệ chính. **Chọn `catch` dựa trên ngoại lệ chính được ném ra**, không lần lượt chạy các nhánh cho từng suppressed exception.

Vì vậy, một multi-catch dùng để bọc lỗi nên giữ nguyên đối tượng `ex` làm cause. Danh sách `suppressed` gắn với đối tượng nguyên nhân sẽ còn có thể được truy nguyên. Việc chỉ sao chép message bỏ qua nhiều thông tin hữu ích khi phân tích sự cố.

Nếu chính khối `catch` hoặc `finally` lại ném lỗi mới, phải phân biệt lỗi **được chọn để bắt ban đầu** và lỗi **đi ra ngoài**. Các nhánh `catch` kế bên không nhận ngoại lệ mới vừa phát sinh trong chính nhánh `catch`.

## 16. Những sai lầm thường gặp

| Sai lầm | Hậu quả | Cách khắc phục |
| --- | --- | --- |
| Gom mọi lỗi vì “cùng ghi log”. | Các chính sách phục hồi khác nhau bị trộn lẫn. | So sánh kết quả cuối cùng, retry, rollback, fallback, hủy tác vụ trước khi gộp. |
| Viết `catch (Exception ex)` thay cho các kiểu cần xử lý. | Có thể vô tình nuốt `RuntimeException` không liên quan. | Dùng multi-catch liệt kê đúng các kiểu nếu cùng chính sách. |
| Liệt kê cả lớp cha và lớp con trong multi-catch. | Lỗi biên dịch. | Bắt lớp cha nếu thật sự cùng xử lý; hoặc tách nhánh con trước cha. |
| Gán lại tham số multi-catch. | Lỗi biên dịch do tham số implicitly final. | Giữ `ex` nguyên vẹn; tạo biến khác nếu cần. |
| Bọc lỗi chỉ bằng `ex.getMessage()`. | Mất `cause`, khó truy stack và suppressed exception. | Truyền `ex` vào constructor nhận `Throwable cause`. |
| Rethrow `ex` nhưng cho rằng tầng hiện tại đã xử lý xong. | Lỗi tiếp tục đi lên; tầng trên vẫn có trách nhiệm. | Khai báo `throws` phù hợp hoặc bắt ở tầng ranh giới. |
| Gộp `InterruptedException` với lỗi I/O rồi tiếp tục. | Có thể mất tín hiệu hủy thread. | Tách và truyền interrupt hoặc khôi phục cờ, kết thúc tác vụ. |
| Cố bắt checked exception không thể phát sinh từ `try`. | Compiler có thể báo “never thrown”. | Căn cứ vào chữ ký và nguồn ngoại lệ thực tế của `try`. |
| Gộp `Throwable` hay `Error` vào một nhánh xử lý nghiệp vụ thông thường. | Có thể che khuất sự cố nghiêm trọng và giả định phục hồi sai. | Giới hạn kiểu cần bắt vào các ngoại lệ có chiến lược rõ ràng. |
| Dùng fallback bất kể dữ liệu có an toàn không. | Hệ thống có thể trả kết quả sai hoặc không nhất quán. | Chỉ fallback khi có hợp đồng và điều kiện kiểm chứng hợp lệ. |

## 17. Checklist quyết định trước khi viết multi-catch

Khi cân nhắc `catch (A | B ex)`, hãy lần lượt kiểm tra:

1. **A và B độc lập về kiểu?** Nếu một kiểu kế thừa kiểu kia, cú pháp multi-catch không hợp lệ.
2. **Khối `try` có thể ném cả A và B theo quy tắc compile-time?** Đặc biệt quan trọng với checked exceptions.
3. **Cả hai cùng một cách xử lý từ đầu đến cuối?** Xem cả fallback, retry, rollback, thông báo và trạng thái cuối.
4. **Quyết định xử lý chung có thật sự an toàn?** Không áp dụng chính sách “trông có vẻ hợp lý” cho lỗi có thể phá vỡ bất biến.
5. **Lỗi gốc có được giữ?** Khi rethrow, dùng `throw ex;`; khi translation, dùng constructor nhận `cause`.
6. **Tầng gọi biết điều gì?** Nếu đã phục hồi, trả kết quả hợp lệ; nếu chưa, khai báo checked exception còn có thể thoát.
7. **Có yêu cầu interrupt hoặc dọn dẹp riêng?** Nếu có, cân nhắc nhánh riêng hoặc logic quản lý tài nguyên rõ ràng.

Nếu mọi câu trả lời ủng hộ cùng hành động, multi-catch giúp mã ngắn hơn và giữ chính sách nhất quán. Nếu có khác biệt quan trọng, các `catch` riêng giúp diễn đạt đúng cách hệ thống cần phản ứng.

## 18. Tự kiểm tra

**Câu 1.** `IOException` và `SQLException` đều phải chuyển thành `DataLoadException`, cùng message và giữ cause. Dùng chung multi-catch có hợp lý không?  
**Có thể hợp lý.** Hai lớp không có quan hệ cha/con và cùng chính sách translation tại ranh giới tầng. Kiểm tra rằng `try` có thể ném cả hai loại checked.

**Câu 2.** `FileNotFoundException` và `IOException` đều dùng cùng hành động. Viết `catch (FileNotFoundException | IOException ex)` được không?  
**Không.** Multi-catch cấm kiểu thay thế là lớp con của kiểu thay thế khác. Có thể dùng `catch (IOException ex)` nếu tất cả `IOException` thuộc phạm vi cần xử lý chung, hoặc dùng các nhánh riêng.

**Câu 3.** Hai `catch` cùng gọi hàm ghi log nhưng một nhánh return fallback còn nhánh kia rethrow. Có nên gộp không?  
**Thường không.** Chính sách cuối khác nhau; trích hàm ghi log chung mà giữ hai nhánh xử lý là cách rõ ràng hơn.

**Câu 4.** Một multi-catch có chạy hai lần khi cả hai kiểu đều được khai báo?  
**Không.** Một ngoại lệ chính được chọn xử lý trong một nhánh duy nhất tại thời điểm đó.

**Câu 5.** Có thể viết `ex = replacement;` trong `catch (IOException | SQLException ex)`?  
**Không.** Tham số multi-catch là implicitly final.

**Câu 6.** `throw ex;` và `throw new DataLoadException("failed", ex);` có giống nhau không?  
**Không.** Cách đầu ném lại cùng đối tượng; cách sau tạo ngoại lệ mới thuộc hợp đồng khác và liên kết nguyên nhân qua cause.

**Câu 7.** Có nên gom `InterruptedException` với `IOException`, in message rồi cho worker tiếp tục?  
**Không nên**, vì thao tác chờ có thể đã xóa cờ interrupt, làm yêu cầu hủy bị bỏ qua. Cần chính sách interrupt đúng.

**Câu 8.** Nếu `try` chỉ có khả năng ném `IOException` checked, tự thêm `SQLException` vào multi-catch có luôn được không?  
**Không.** Java có kiểm tra checked exception có thể được ném từ `try`; loại không có khả năng phát sinh có thể bị từ chối.

## Tham khảo

- [Java Language Specification, Java SE 17 — §14.20 The `try` Statement, multi-catch](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20).
- [JLS 17 — §14.20.1 Execution of `try-catch`](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.1).
- [JLS 17 — §11.2.2 Exception Analysis of Statements (precise rethrow)](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.2.2).
- [JLS 17 — §11.2.3 Exception Checking](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.2.3).
- [JLS 17 — §4.10.4 Least Upper Bound (kiểu chung của multi-catch)](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.10.4).

**Ghi nhớ:** Nhiều ngoại lệ có thể dùng chung một cách xử lý khi **đều dẫn đến cùng quyết định đúng và an toàn**. Dùng multi-catch cho chính sách chung, tách nhánh khi ý nghĩa hoặc cách phục hồi khác nhau, và luôn giữ thông tin nguyên nhân khi dịch hoặc ném lại lỗi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multi-catch-rethrow-003">Không đặt kiểu cha và kiểu con trong cùng multi-catch</a>

<details>
<summary>Xem chi tiết</summary>

# Không đặt kiểu cha và kiểu con trong cùng multi-catch của Java

Trong Java, một nhánh **multi-catch** cho phép nhiều loại ngoại lệ dùng chung một phần xử lý:

~~~java
try {
    performOperation();
} catch (IOException | SQLException ex) {
    handleFailure(ex);
}
~~~

Đây là **đoạn minh họa**; `performOperation()` và `handleFailure(...)` cần được định nghĩa phù hợp. Dấu `|` giữa các kiểu ngoại lệ có nghĩa là “bắt một trong các loại được liệt kê”. Tuy nhiên, **không được liệt kê cùng lúc một kiểu cha và một kiểu con của nó trong cùng một multi-catch**:

~~~java
// KHÔNG HỢP LỆ: FileNotFoundException là lớp con của IOException.
catch (FileNotFoundException | IOException ex) {
    // ...
}
~~~

Đây là lỗi **biên dịch**, không phải lỗi chỉ phát sinh khi chạy. Nếu chỉ cần xử lý toàn bộ họ ngoại lệ theo một cách, hãy **bắt một kiểu cha duy nhất**. Nếu cần phân biệt cách xử lý loại cụ thể và loại tổng quát, hãy **dùng các `catch` riêng biệt, kiểu con đứng trước kiểu cha**. Nếu hai kiểu không kế thừa nhau và dùng chung logic xử lý, **multi-catch là lựa chọn hợp lệ**.

Bài học này trình bày quy tắc, lý do, cách xác định quan hệ kế thừa, cách sửa những lỗi thường gặp, tác động đến việc ném lại ngoại lệ và các ví dụ Java 17+ đã biên dịch/chạy kiểm chứng.

## 1. Nhắc lại: multi-catch dùng để làm gì?

Từ Java 7, một `catch` có thể liệt kê nhiều kiểu ngoại lệ độc lập bằng dấu `|` nếu cùng dùng một thân xử lý:

~~~java
try {
    performOperation();
} catch (java.io.IOException | java.sql.SQLException ex) {
    System.err.println(ex.getMessage());
}
~~~

Đây là đoạn minh họa cần `performOperation()` khai báo các checked exception tương ứng. Một ngoại lệ chỉ kích hoạt **một** handler phù hợp; dấu `|` không tạo nhiều biến hay nhiều lượt xử lý. Với bài này, trọng tâm không phải học lại cú pháp mà là **quan hệ subtype**: dù `A` và `B` đều thuộc `Exception`, một `catch (A | B e)` bị từ chối khi `A` kế thừa `B` hoặc ngược lại.

## 2. Vì sao không được ghép kiểu cha và kiểu con?

Xét phân cấp quen thuộc:

~~~text
java.lang.Exception
└── java.io.IOException
    ├── java.io.FileNotFoundException
    ├── java.io.EOFException
    ├── java.nio.file.FileSystemException
    │   └── java.nio.file.AccessDeniedException
    └── ...
~~~

`FileNotFoundException` là lớp con của `IOException`, tức:

~~~java
// Một FileNotFoundException cũng là một IOException.
IOException error = new java.io.FileNotFoundException("thiếu tệp");
~~~

Nếu một nhánh `catch` đã bắt `IOException`, nó **đương nhiên bắt được** mọi `FileNotFoundException` (và các lớp con khác của `IOException`) phát sinh từ phạm vi `try` đang bảo vệ. Do đó đoạn:

~~~java
// KHÔNG BIÊN DỊCH.
catch (FileNotFoundException | IOException ex) {
    System.err.println(ex.getMessage());
}
~~~

chứa một lựa chọn **thừa về mặt bao phủ kiểu**. Java quy định các lựa chọn trong một multi-catch phải độc lập xét theo quan hệ kế thừa: không được có kiểu nào là lớp con của kiểu còn lại. Đảo thứ tự thành `IOException | FileNotFoundException` **vẫn sai**.

Quy tắc áp dụng cho **quan hệ kế thừa trực tiếp lẫn gián tiếp**. Ví dụ, `AccessDeniedException` là lớp con gián tiếp của `IOException` qua `FileSystemException`, nên `IOException | AccessDeniedException` cũng không hợp lệ.

Vì vậy, việc kiểm tra không chỉ nhìn hai tên có giống nhau hay không; phải đối chiếu **quan hệ subtype trong cây `Throwable`**.

## 3. Ví dụ biên dịch thất bại và cách đọc thông báo của `javac`

Tệp `InvalidMultiCatch.java` dưới đây được viết cố ý để minh họa lỗi; **không thể biên dịch thành chương trình chạy**:

~~~java
import java.io.FileNotFoundException;
import java.io.IOException;

class InvalidMultiCatch {
    static void run() {
        try {
            throw new FileNotFoundException("missing");
        } catch (FileNotFoundException | IOException ex) {
            System.out.println(ex.getMessage());
        }
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 InvalidMultiCatch.java
~~~

`javac` của JDK hiện đại báo lỗi chứa các dòng như:

~~~text
error: Alternatives in a multi-catch statement cannot be related by subclassing
Alternative FileNotFoundException is a subclass of alternative IOException
~~~

Vị trí dấu mũ trong thông báo thường trỏ đến một lựa chọn trong danh sách `catch`; đường dẫn, số dòng và trình bày lỗi phụ thuộc môi trường. Trọng tâm là: **một lựa chọn là lớp con của lựa chọn khác trong cùng multi-catch**.

Đây không phải lỗi vì `try` chỉ phát sinh một ngoại lệ hoặc vì các nhánh `catch` sai thứ tự. Ngay cả khi `try` thật sự có thể phát sinh cả `IOException` và `FileNotFoundException`, kiểu con vẫn đã được kiểu cha bao phủ, nên không được viết cả hai như những lựa chọn cùng cấp trong multi-catch.

## 4. Cách sửa thứ nhất: bắt một kiểu cha khi dùng chung cách xử lý

Nếu tất cả lỗi trong họ `IOException` đều cần cùng một cách xử lý, hãy viết:

~~~java
import java.io.IOException;

// Đoạn minh họa nằm trong phương thức phù hợp.
try {
    readData();
} catch (IOException ex) {
    System.err.println("Không đọc được dữ liệu: " + ex.getMessage());
}
~~~

`catch (IOException ex)` bắt được `FileNotFoundException`, `EOFException` và mọi lớp con của `IOException` phát sinh trong phạm vi `try`. Không cần viết:

~~~java
// Sai:
catch (IOException | FileNotFoundException | EOFException ex) {
    // ...
}
~~~

Ưu điểm của một `catch (IOException)` là đơn giản, không lặp lại kiểu con. Đổi lại, nó có thể **bắt cả những ngoại lệ I/O khác** ngoài hai loại bạn ban đầu muốn xử lý, như `AccessDeniedException`. Nếu các loại đó cần hành vi khác, nên chọn cách xử lý tách nhánh trong phần tiếp theo.

Không vì mục đích sửa lỗi cú pháp mà tự động mở rộng lên `catch (Exception)` hoặc `catch (Throwable)`: kiểu rộng hơn có thể che các lỗi bạn không định xử lý. Hãy chọn kiểu cha **đủ hẹp so với hợp đồng và cách phục hồi dự kiến**.

## 5. Cách sửa thứ hai: dùng các `catch` riêng khi cần xử lý khác nhau

Nếu muốn xử lý `FileNotFoundException` riêng và dành xử lý tổng quát cho các `IOException` còn lại, hãy dùng **hai nhánh `catch` riêng**:

~~~java
try {
    readData();
} catch (FileNotFoundException ex) {
    System.err.println("Hãy kiểm tra đường dẫn tệp.");
} catch (IOException ex) {
    System.err.println("Lỗi I/O khác: " + ex.getMessage());
}
~~~

Đây là **đoạn minh họa**, cần các `import` và phương thức `readData()` phù hợp. Cách viết này hợp lệ vì Java kiểm tra các nhánh **theo thứ tự**, từ trên xuống dưới. Nếu lỗi thực sự là `FileNotFoundException`, nó vào nhánh đầu; nếu là một `IOException` khác, nó đến nhánh thứ hai.

**Phải đặt kiểu con trước kiểu cha**. Viết ngược:

~~~java
// KHÔNG HỢP LỆ: nhánh sau không thể bắt thêm gì.
try {
    readData();
} catch (IOException ex) {
    System.err.println("I/O");
} catch (FileNotFoundException ex) {
    System.err.println("Thiếu tệp");
}
~~~

sẽ khiến `catch (FileNotFoundException)` bị xem là không thể tới, bởi `catch (IOException)` trước đó đã bao phủ kiểu con.

Lưu ý quan trọng: **quy tắc multi-catch và quy tắc nhiều catch riêng không giống nhau về cách sửa**:

- **Trong cùng một multi-catch:** `catch (FileNotFoundException | IOException ex)` **không hợp lệ ở bất kỳ thứ tự nào**.
- **Trong hai `catch` riêng:** `catch (FileNotFoundException)` rồi `catch (IOException)` **hợp lệ**, nhờ ưu tiên xử lý loại cụ thể trước.
- **Hai `catch` riêng theo thứ tự ngược:** `catch (IOException)` rồi `catch (FileNotFoundException)` **không hợp lệ** vì nhánh cụ thể bị che.

## 6. Ví dụ Java 17+ hoàn chỉnh: xử lý kiểu con trước kiểu cha

Tạo tệp `HierarchyCatchDemo.java`:

~~~java
import java.io.FileNotFoundException;
import java.io.IOException;

public class HierarchyCatchDemo {
    static void load(boolean missing) throws IOException {
        if (missing) {
            throw new FileNotFoundException("thiếu tài liệu");
        }
        throw new IOException("lỗi đọc chung");
    }

    public static void main(String[] args) {
        for (boolean missing : new boolean[] {true, false}) {
            try {
                load(missing);
            } catch (FileNotFoundException ex) {
                System.out.println("Thiếu tệp: " + ex.getMessage());
            } catch (IOException ex) {
                System.out.println("I/O tổng quát: " + ex.getMessage());
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 HierarchyCatchDemo.java
java HierarchyCatchDemo
~~~

Stdout chính xác:

~~~text
Thiếu tệp: thiếu tài liệu
I/O tổng quát: lỗi đọc chung
~~~

Phân tích:

1. Trong lượt đầu, `load(true)` ném `FileNotFoundException`. Nhánh cụ thể bắt lỗi, nhánh tổng quát không chạy.
2. Lượt tiếp theo, `load(false)` ném một đối tượng `IOException`. Nhánh đầu không phù hợp; nhánh thứ hai xử lý.
3. Hai nhánh có thể dùng thông điệp và phương án phục hồi khác nhau. Nếu chúng hoàn toàn giống nhau, có thể thay cả hai bằng một `catch (IOException)`.

Đừng nhầm `throws IOException` trong chữ ký `load` với kiểu runtime của ngoại lệ thực tế. Chữ ký khai báo kiểu lỗi có thể truyền ra; `catch` lựa chọn nhánh dựa trên **đối tượng thực tế** khi chạy.

## 7. Cách sửa thứ ba: multi-catch những kiểu cùng nhánh nhưng không kế thừa nhau

Hai kiểu **cùng có cha là `IOException`** không có nghĩa một kiểu là con của kiểu kia. Chẳng hạn `FileNotFoundException` và `EOFException` đều là lớp con của `IOException`, nhưng **không có quan hệ kế thừa trực tiếp hoặc gián tiếp giữa hai kiểu ấy**. Chúng có thể đứng cùng một multi-catch:

~~~java
catch (FileNotFoundException | EOFException ex) {
    System.err.println("Nhóm lỗi tệp đặc biệt: " + ex.getMessage());
}
~~~

Bạn có thể thêm `catch (IOException ex)` **ở một nhánh riêng phía sau** để xử lý các lỗi I/O còn lại. Cấu trúc này phù hợp khi hai loại cụ thể cần một cách xử lý chung, nhưng các loại I/O khác lại cần cách xử lý khác.

Ví dụ hoàn chỉnh `MultiCatchSiblingsDemo.java`:

~~~java
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;

public class MultiCatchSiblingsDemo {
    static void load(String mode) throws IOException {
        switch (mode) {
            case "missing" -> throw new FileNotFoundException("thiếu tệp");
            case "ended" -> throw new EOFException("hết dữ liệu");
            case "denied" -> throw new IOException("không có quyền");
            default -> System.out.println("Đọc thành công");
        }
    }

    public static void main(String[] args) {
        for (String mode : new String[] {"missing", "ended", "denied", "ok"}) {
            try {
                load(mode);
            } catch (FileNotFoundException | EOFException ex) {
                System.out.println("Nhóm cụ thể: " + ex.getClass().getSimpleName()
                        + " - " + ex.getMessage());
            } catch (IOException ex) {
                System.out.println("I/O khác: " + ex.getMessage());
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 MultiCatchSiblingsDemo.java
java MultiCatchSiblingsDemo
~~~

Stdout chính xác:

~~~text
Nhóm cụ thể: FileNotFoundException - thiếu tệp
Nhóm cụ thể: EOFException - hết dữ liệu
I/O khác: không có quyền
Đọc thành công
~~~

Tại sao được phép?

- Hai lựa chọn `FileNotFoundException` và `EOFException` không có quan hệ kiểu cha–kiểu con với nhau.
- `catch (IOException)` là **nhánh riêng tiếp theo**, không phải lựa chọn `| IOException` trong nhánh multi-catch trước.
- Nếu `load` ném `FileNotFoundException` hay `EOFException`, chúng đi vào multi-catch đầu.
- Nếu `load` ném một `IOException` không khớp hai kiểu cụ thể, nó được xử lý ở nhánh `catch (IOException)` tiếp theo.
- Với `"ok"`, `load` hoàn thành bình thường nên các `catch` không chạy.

Ví dụ dùng `switch` expression-style arrow trong câu lệnh `switch`, tương thích Java 17. Những lỗi trong chương trình là ngoại lệ chủ động ném để kết quả minh họa ổn định; chúng không phụ thuộc vào một tệp thật có tồn tại hay không.

## 8. Các cặp kiểu: hợp lệ hay không?

| Các lựa chọn trong cùng `catch (... | ... ex)` | Hợp lệ? | Giải thích |
| --- | --- | --- |
| `IOException | SQLException` | Có | Hai lớp ngoại lệ không kế thừa nhau. |
| `FileNotFoundException | EOFException` | Có | Cùng nhánh `IOException`, nhưng là hai nhánh con độc lập. |
| `IllegalArgumentException | IllegalStateException` | Có | Cùng nhánh `RuntimeException`, không phải lớp con của nhau. |
| `IOException | RuntimeException` | Có | Hai loại thuộc hai nhánh khác nhau của `Exception`. |
| `IOException | FileNotFoundException` | Không | `FileNotFoundException` kế thừa `IOException`. |
| `Exception | IOException` | Không | `IOException` kế thừa `Exception`. |
| `RuntimeException | IllegalArgumentException` | Không | `IllegalArgumentException` kế thừa `RuntimeException`. |
| `IllegalArgumentException | NumberFormatException` | Không | `NumberFormatException` kế thừa `IllegalArgumentException`. |
| `Throwable | Error` | Không | `Error` kế thừa `Throwable`. |
| `IOException | AccessDeniedException` | Không | `AccessDeniedException` là lớp con gián tiếp của `IOException`. |

Một cặp **không có quan hệ subtype** mới chỉ thỏa quy tắc về các lựa chọn của multi-catch. Toàn bộ câu lệnh còn phải thỏa quy tắc khác, ví dụ `try` có thể phát sinh những checked exception được bắt, thứ tự các nhánh trong cấu trúc lớn hơn và khả năng truy cập các kiểu/import.

Do đó, “hợp lệ” trong bảng có nghĩa **không vi phạm quy tắc quan hệ kế thừa của multi-catch**, không phải bất kỳ chương trình nào chứa cặp này cũng chắc chắn biên dịch.

## 9. `catch` chỉ xét kiểu runtime, không chọn theo vị trí trong danh sách `|`

Một số người viết:

~~~java
catch (IllegalArgumentException | IllegalStateException ex) {
    System.out.println(ex.getClass().getSimpleName());
}
~~~

rồi tưởng rằng Java sẽ kiểm tra từ trái sang phải và giữ một thứ tự ưu tiên tương tự `catch` riêng. Thực tế, **cả hai lựa chọn dẫn đến cùng một khối xử lý**; không có hai nhánh khác nhau để chọn. Đối tượng bị bắt vẫn có **kiểu runtime thực tế**, ví dụ `IllegalArgumentException` hoặc `IllegalStateException`; `ex.getClass()` cho biết lớp thực tế ấy.

Trong khi đó:

~~~java
catch (IllegalArgumentException ex) {
    // Xử lý lỗi đối số.
} catch (IllegalStateException ex) {
    // Xử lý lỗi trạng thái.
}
~~~

là hai nhánh riêng, mỗi nhánh có logic xử lý khác nhau. Bạn nên dùng multi-catch khi **thật sự muốn xử lý chung**; đừng gộp cho ngắn nếu lỗi khác nhau đòi hỏi hành động khác nhau.

Tương tự, **đảo vị trí** `FileNotFoundException | IOException` thành `IOException | FileNotFoundException` không sửa được lỗi compiler vì quy tắc kiểm tra **quan hệ kiểu**, không phải ưu tiên thứ tự trong dấu `|`.

## 10. Kiểu của biến trong multi-catch và điều cần chú ý

Trong một nhánh multi-catch như:

~~~java
catch (FileNotFoundException | EOFException ex) {
    System.out.println(ex.getMessage());
}
~~~

compiler suy luận kiểu tham chiếu có thể sử dụng an toàn dựa trên các lựa chọn; ở ví dụ này, những phương thức chung từ `IOException`/`Throwable` như `getMessage()` đều dùng được. Kiểu runtime của đối tượng thực tế vẫn giữ nguyên.

Nhưng bạn không thể gọi trực tiếp phương thức chỉ tồn tại trên **một trong các lựa chọn** nếu kiểu tĩnh của `ex` không bảo đảm có phương thức đó. Khi thực sự cần hành vi riêng của mỗi kiểu, hãy tách thành những `catch` riêng hoặc kiểm tra kiểu đúng cách trong khối xử lý.

### Tham số multi-catch không được gán lại

Đoạn sau **không biên dịch**:

~~~java
// Ví dụ minh họa lỗi khi gán lại tham số multi-catch.
try {
    performOperation();
} catch (IOException | SQLException ex) {
    ex = new IOException("thay thế");
}
~~~

Tham số `ex` của multi-catch được coi là final theo quy tắc Java. Nếu cần tạo một ngoại lệ mới để truyền ra ngoài, viết `throw new ...` hoặc đặt tham chiếu mới vào **biến khác**. Không nên thay thế tham chiếu đối tượng gốc rồi vô tình làm mất thông tin chẩn đoán.

Việc gán lại biến không liên quan tới quy tắc cha–con, nhưng là một lỗi thường gặp khi chuyển từ nhiều nhánh `catch` sang multi-catch.

### Không thể dùng cả kiểu cha và kiểu con để “cung cấp nhiều phương thức hơn”

Nếu muốn gọi một API đặc thù của `FileNotFoundException`, thêm `IOException` vào danh sách `|` không giúp gì; nó chỉ tạo lỗi biên dịch. Lựa chọn đúng là bắt `FileNotFoundException` ở nhánh riêng, hoặc làm việc với giao diện chung của kiểu cha khi phù hợp.

## 11. `throw ex` bên trong multi-catch và kiểm tra checked exception

Câu lệnh `throw ex;` ném lại **chính đối tượng ngoại lệ** đã bắt. Dù hai lựa chọn trong multi-catch hợp lệ, compiler vẫn phải bảo đảm các checked exception có thể truyền ra được bắt hoặc khai báo.

Ví dụ:

~~~java
import java.io.IOException;
import java.sql.SQLException;

// Đoạn minh họa bên trong một lớp.
static void process() throws IOException, SQLException {
    try {
        operation(); // Có thể ném IOException hoặc SQLException.
    } catch (IOException | SQLException ex) {
        System.err.println("Không hoàn thành");
        throw ex;
    }
}
~~~

Đây là **đoạn minh họa**, cần định nghĩa `operation()` với chữ ký phù hợp. Java hỗ trợ phân tích ném lại ngoại lệ tương đối chính xác trong một số trường hợp, và ở ví dụ này `throws IOException, SQLException` bao phủ những checked exception cần công bố.

Không được sửa ví dụ thành `catch (IOException | FileNotFoundException ex)` chỉ vì cả hai loại đều có thể được `operation()` ném: quan hệ cha–con vẫn là lỗi cú pháp/kiểu của multi-catch, độc lập với việc bạn định ném lại.

Cần phân biệt:

- **`throw ex`**: ném lại đối tượng đã bắt, không tự tạo ngoại lệ mới.
- **`throw new SomeException(..., ex)`**: tạo ngoại lệ mới, giữ `ex` làm nguyên nhân khi constructor hỗ trợ.
- **`throws IOException, SQLException`**: khai báo checked exception có thể thoát từ phương thức, dùng dấu phẩy. Quy tắc cấm cha–con của multi-catch **không phải quy tắc cấm mọi danh sách `throws`**.
- **`catch (A | B ex)`**: danh sách lựa chọn trong *một nhánh xử lý*, dùng `|` và cấm quan hệ cha–con giữa hai lựa chọn.

Nếu chỉ muốn xử lý một họ `IOException` rồi ném lại, `catch (IOException ex) { throw ex; }` đã đủ; không cần liệt kê toàn bộ các lớp con.

## 12. Phân biệt multi-catch với nhiều `catch` riêng trong tình huống thực tế

Khi quyết định cấu trúc bắt lỗi, hãy bắt đầu từ **hành vi mong muốn**, không phải từ ý định gom nhiều tên exception vào cùng một dòng.

### Trường hợp A: xử lý mọi ngoại lệ I/O như nhau

~~~java
catch (IOException ex) {
    reportIoFailure(ex);
}
~~~

Một kiểu cha duy nhất phù hợp. Đây là phương án đơn giản nhất nếu tất cả `IOException` được xử lý chung.

### Trường hợp B: một loại cần phương án riêng, các loại còn lại xử lý chung

~~~java
catch (FileNotFoundException ex) {
    askForAnotherPath(ex);
} catch (IOException ex) {
    reportIoFailure(ex);
}
~~~

Hai nhánh riêng, kiểu con trước kiểu cha. Đây là cấu trúc đúng khi hành vi khác nhau và cần một nhánh dự phòng tổng quát.

### Trường hợp C: một số loại ngang hàng cùng xử lý, loại khác có nhánh riêng

~~~java
catch (FileNotFoundException | EOFException ex) {
    reportInputUnavailable(ex);
} catch (IOException ex) {
    reportOtherIoFailure(ex);
}
~~~

Multi-catch cho các loại không kế thừa nhau; nhánh kiểu cha tách riêng ở phía sau. Cách này phân nhóm điều kiện thất bại cụ thể mà vẫn có nhánh dự phòng.

### Trường hợp D: những loại ngoại lệ thuộc hai họ độc lập

~~~java
catch (IOException | SQLException ex) {
    reportExternalFailure(ex);
}
~~~

Có thể dùng một nhánh multi-catch vì hai kiểu không có quan hệ kế thừa. Tuy nhiên chỉ gộp nếu chúng thật sự có chung cách xử lý; một số trường hợp lỗi cơ sở dữ liệu đòi hỏi rollback trong khi lỗi I/O đòi hỏi chiến lược phục hồi khác, khi đó tách nhánh rõ ràng hơn.

Các tên phương thức `reportIoFailure`, `askForAnotherPath`, `reportInputUnavailable`, `reportOtherIoFailure`, `reportExternalFailure` trong các đoạn minh họa cần được định nghĩa trước khi biên dịch; những ví dụ có chương trình chạy hoàn chỉnh đã được cung cấp ở mục 6 và 7.

## 13. Lỗi thường gặp khi viết hoặc sửa multi-catch

| Sai lầm | Tại sao sai? | Cách sửa |
| --- | --- | --- |
| `catch (IOException | FileNotFoundException ex)` | Kiểu con nằm trong cùng multi-catch với kiểu cha. | Chỉ bắt `IOException`, hoặc tách hai `catch` theo đúng thứ tự. |
| `catch (IllegalArgumentException | NumberFormatException ex)` | `NumberFormatException extends IllegalArgumentException`. | Bắt `IllegalArgumentException` nếu xử lý chung, hoặc bắt loại cụ thể ở nhánh riêng trước. |
| Đảo thành `FileNotFoundException | IOException` để thử sửa lỗi | Quy tắc không phụ thuộc thứ tự lựa chọn. | Thay đổi cấu trúc, không chỉ hoán vị. |
| Đặt `catch (IOException)` trước `catch (FileNotFoundException)` | Nhánh thứ hai riêng biệt bị che bởi nhánh cha. | Đưa `FileNotFoundException` lên trước. |
| Dùng `catch (Exception | IOException ex)` | `IOException` là lớp con của `Exception`. | Chọn kiểu cha cần thiết, hoặc tách nhánh có hành vi riêng. |
| Nghĩ hai kiểu có cùng lớp cha thì không được gộp | Mọi lớp đều có tổ tiên chung; điều cấm là hai lựa chọn kế thừa **nhau**. | `FileNotFoundException | EOFException` hợp lệ xét quan hệ subtype. |
| Dùng dấu `||` hoặc dấu phẩy giữa các loại `catch` | Không đúng cú pháp multi-catch. | Dùng một dấu `|` cho các lựa chọn. |
| Gộp các lỗi có chính sách xử lý khác nhau | Cùng một khối khó phân biệt hành vi và có thể che thông tin. | Dùng những `catch` riêng khi cần. |
| Dùng kiểu cha quá rộng chỉ để làm mã biên dịch | Có thể bắt thêm lỗi không thuộc trách nhiệm xử lý. | Chọn kiểu cha phù hợp phạm vi lỗi thật sự. |
| Gán lại tham số multi-catch `ex` | Tham số multi-catch là final. | Dùng biến khác hoặc ném ngoại lệ mới đúng mục đích. |
| Suy ra chương trình chạy được chỉ vì hai kiểu không kế thừa nhau | Checked exception và các ràng buộc khác vẫn được kiểm tra. | Kiểm tra toàn bộ `try`, các nhánh `catch`, `throws` và import. |

Một mẹo kiểm tra trước khi viết multi-catch: đối với **mỗi cặp kiểu được đặt hai bên dấu `|`**, hỏi “A có thể gán cho biến kiểu B không, hoặc B có thể gán cho biến kiểu A không, do kế thừa?” Nếu có, đó là tín hiệu có quan hệ subtype, không nên để chúng nằm cùng một nhánh multi-catch.

## 14. Bài tập tự kiểm tra

**Bài 1.** Vì sao `catch (IOException | FileNotFoundException ex)` không hợp lệ?

**Đáp án:** `FileNotFoundException` kế thừa `IOException`. Một đối tượng kiểu `FileNotFoundException` vốn đã khớp `IOException`, nên hai lựa chọn không được đứng cùng một multi-catch.

**Bài 2.** Đổi thứ tự thành `catch (FileNotFoundException | IOException ex)` có sửa được không?

**Đáp án:** Không. Quan hệ kiểu cha–con vẫn tồn tại bất kể thứ tự.

**Bài 3.** Nếu `FileNotFoundException` cần thông báo riêng, còn các `IOException` khác cần thông báo chung, nên viết gì?

**Đáp án:** Hai nhánh riêng: `catch (FileNotFoundException ex) { ... }` trước, `catch (IOException ex) { ... }` sau.

**Bài 4.** `catch (FileNotFoundException | EOFException ex)` có vi phạm quy tắc này không?

**Đáp án:** Không. Hai kiểu là các lớp con độc lập của `IOException`; chúng không là lớp con của nhau. Chương trình vẫn phải thỏa các kiểm tra còn lại về checked exception.

**Bài 5.** `catch (IllegalArgumentException | NumberFormatException ex)` có hợp lệ không?

**Đáp án:** Không; `NumberFormatException` kế thừa `IllegalArgumentException`.

**Bài 6.** Nếu chỉ muốn một cách xử lý cho toàn bộ `IOException`, nên làm gì?

**Đáp án:** Dùng một `catch (IOException ex)`. Không cần liệt kê các lớp con.

**Bài 7.** Có thể có `catch (FileNotFoundException | EOFException ex)` rồi tiếp theo `catch (IOException ex)` không?

**Đáp án:** Có, nếu `try` và các quy tắc khác hợp lệ. Kiểu cha nằm trong nhánh **riêng** phía sau, không phải trong cùng danh sách `|`.

**Bài 8.** `throws IOException, FileNotFoundException` có bị cấm bởi quy tắc multi-catch không?

**Đáp án:** Không. `throws` và `catch (A | B)` có cú pháp và quy tắc khác nhau; tuy nhiên khai báo cả kiểu cha và kiểu con trong `throws` thường dư thừa về phạm vi lỗi.

**Bài 9.** `catch (Exception | RuntimeException ex)` có hợp lệ không?

**Đáp án:** Không, vì `RuntimeException` là lớp con của `Exception`.

**Bài 10.** Khi nào việc sửa một multi-catch sai thành hai `catch` riêng có ý nghĩa hơn việc xóa kiểu con?

**Đáp án:** Khi lỗi cụ thể phải được xử lý khác với các lỗi tổng quát, chẳng hạn cho người dùng chọn đường dẫn mới khi thiếu tệp nhưng báo lỗi hệ thống cho `IOException` còn lại.

## Tài liệu đối chiếu

- [Java Language Specification 17, §14.20 — The `try` Statement](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20): quy tắc cú pháp `catch`, multi-catch, tính hợp lệ của các lựa chọn và xử lý.
- [Java Language Specification 17, §11.2 — Compile-Time Checking of Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.2): kiểm tra những checked exception có thể bị ném và được bắt.
- [Oracle Java Tutorials — Catching Multiple Exception Types and Rethrowing Exceptions](https://docs.oracle.com/javase/tutorial/essential/exceptions/multiple.html): ví dụ multi-catch và lý do không cho các lựa chọn có quan hệ subtype (hướng dẫn viết theo Java 7/8, quy tắc vẫn áp dụng trên Java 17).
- [Java SE 17 API — IOException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/IOException.html), [FileNotFoundException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/FileNotFoundException.html) và [EOFException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/EOFException.html): đối chiếu quan hệ kế thừa của các loại I/O.

**Ghi nhớ:** trong **một** `catch (A | B ex)`, A và B không được là kiểu cha–kiểu con của nhau. Nếu cần bắt chung toàn bộ nhánh, bắt **kiểu cha**; nếu cần xử lý riêng, viết **các `catch` tách biệt theo thứ tự con trước cha**; nếu hai kiểu độc lập và logic giống nhau, dùng **multi-catch**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multi-catch-rethrow-004">Tham số multi-catch ngầm định final</a>

<details>
<summary>Xem chi tiết</summary>

# Tham số multi-catch ngầm định `final` trong Java

**Multi-catch** cho phép một khối `catch` xử lý nhiều kiểu ngoại lệ khi các kiểu đó cần cách ứng xử giống nhau:

~~~java
try {
    process();
} catch (java.io.IOException | java.sql.SQLException error) {
    System.out.println("Không xử lý được: " + error.getMessage());
}
~~~

Đây là đoạn minh họa: `process()` đại diện cho thao tác có thể ném các ngoại lệ tương ứng. Dấu `|` nối **các kiểu ngoại lệ**, không phải toán tử OR để kiểm tra biểu thức boolean. Java chọn nhánh khi đối tượng ngoại lệ đang truyền phù hợp với **bất kỳ** kiểu nào trong danh sách.

Một quy tắc đặc biệt của Java là: **tham số của multi-catch được xem là `final` ngầm định**. Vì vậy, trong ví dụ trên, tên `error` có thể dùng để đọc, chuyển cho hàm khác, ném lại hoặc truyền làm `cause`, nhưng **không thể được gán lại để trỏ sang ngoại lệ khác**.

~~~java
catch (java.io.IOException | java.sql.SQLException error) {
    error = new java.io.IOException("Lỗi mới"); // Không biên dịch.
}
~~~

Điều này khác với tham số của `catch` chỉ có một kiểu, vốn không mặc nhiên `final`. Nắm chắc quy tắc này giúp hiểu lỗi biên dịch, phạm vi biến, cách xử lý nhiều ngoại lệ và cơ chế **precise rethrow** (ném lại với phân tích kiểu ngoại lệ chính xác).

## 1. Cú pháp và lý do sử dụng multi-catch

Trước khi có multi-catch (Java 7), hai loại lỗi được xử lý giống nhau thường phải viết thành hai nhánh riêng:

~~~java
try {
    process();
} catch (java.io.IOException e) {
    report(e);
} catch (java.sql.SQLException e) {
    report(e);
}
~~~

Có thể gộp chúng khi cách xử lý thực sự giống nhau:

~~~java
try {
    process();
} catch (java.io.IOException | java.sql.SQLException e) {
    report(e);
}
~~~

Hai đoạn chỉ minh họa cấu trúc, giả định `process()` và `report(Throwable)` tồn tại. Cách gộp làm giảm lặp code; mỗi khi chỉ một trong hai loại lỗi được ném, **cùng một khối `catch`** được chạy đúng một lần.

Một số điểm cú pháp:

- Phần đầu là `catch (`, theo sau một danh sách **kiểu ngoại lệ** ngăn cách bằng `|`, rồi **một tên tham số duy nhất**.
- `e` là một biến tham chiếu có phạm vi bên trong khối `catch` tương ứng.
- Các kiểu liệt kê phải hợp lệ theo luật `catch` và **không được có quan hệ cha–con** trong cùng một multi-catch.
- Danh sách kiểu hỗ trợ cả checked và unchecked exceptions nếu cấu trúc bắt hợp lệ.
- Nếu mỗi ngoại lệ cần thông tin hoặc hành động khác nhau, tách thành các nhánh `catch` riêng có thể rõ nghĩa hơn.

Multi-catch không tạo ra một lớp ngoại lệ mới có tên `IOException | SQLException`. Đối tượng được bắt vẫn giữ **kiểu runtime ban đầu**.

## 2. `final` ngầm định nghĩa là gì?

Xét mã:

~~~java
catch (java.io.IOException | java.sql.SQLException e) {
    System.out.println(e.getMessage()); // Được đọc.
    log(e);                              // Được truyền làm đối số.
    e = new java.io.IOException();       // Lỗi biên dịch.
}
~~~

Đây là đoạn minh họa thuộc một cấu trúc `try/catch` có đủ điều kiện; giả định `log(Throwable)` tồn tại. Dòng cuối sai vì tham số multi-catch được ngôn ngữ xem là `final`. Việc gán lại tham chiếu `e` không được phép.

Một cách hiểu đúng:

~~~text
Một ngoại lệ được ném
          |
          v
catch (IOException | SQLException e)
          |
          +-- e tham chiếu đến đối tượng ngoại lệ đã được bắt
          |
          +-- đọc / truyền / ném lại e: cho phép
          |
          +-- e = một ngoại lệ khác: bị cấm
~~~

Quy tắc chỉ hạn chế **gán lại biến tham số `e`**; nó không làm mọi đối tượng ngoại lệ trở thành immutable. Chẳng hạn, các thao tác phù hợp trên đối tượng ngoại lệ vẫn có thể được gọi qua tham chiếu `e`. Tuy nhiên không nên tùy tiện thay đổi cause, stack trace hoặc suppressed exceptions, vì những thông tin đó phục vụ chẩn đoán.

`final` ngầm định áp dụng **không phụ thuộc** ngoại lệ đang được bắt thực tế là `IOException` hay `SQLException`. Compiler xác định đặc tính này từ **cú pháp multi-catch** ngay tại thời điểm biên dịch.

### Có bắt buộc viết từ khóa `final` không?

Không. Viết:

~~~java
catch (java.io.IOException | java.sql.SQLException e) {
    System.out.println(e.getMessage());
}
~~~

là đủ để `e` được coi là final ngầm định. Có thể gặp cú pháp khai báo tham số với `final` tường minh:

~~~java
catch (final java.io.IOException | java.sql.SQLException e) {
    System.out.println(e.getMessage());
}
~~~

Kết quả quan trọng giống nhau: `e` không được gán lại. Với multi-catch, viết thêm `final` không phải điều kiện để compiler áp dụng quy tắc.

## 3. So sánh single-catch và multi-catch

| Tính chất | `catch (IOException e)` | `catch (IOException | SQLException e)` |
| --- | --- | --- |
| Số kiểu được liệt kê | Một | Hai hoặc nhiều |
| Tham số mặc nhiên `final`? | Không | **Có** |
| Có thể viết `e = ...`? | Về mặt `final`, có nếu không khai báo `final`; còn phải hợp kiểu | **Không**, luôn báo lỗi biên dịch |
| Có thể đọc `e.getMessage()`? | Có | Có |
| Có thể `throw e;`? | Có, theo quy tắc kiểm tra ngoại lệ | Có, theo quy tắc kiểm tra ngoại lệ và precise rethrow |
| Kiểu của `e` | Kiểu đã khai báo trong nhánh | Kiểu tính từ các alternative, thường chỉ dùng được API chung |
| Có thể truyền `e` làm `Throwable`? | Có | Có |

Ví dụ single-catch **được phép gán lại**:

~~~java
try {
    throw new java.io.IOException("old");
} catch (java.io.IOException e) {
    e = new java.io.IOException("replacement");
    System.out.println(e.getMessage());
}
~~~

Đây là đoạn có thể đặt trong một phương thức Java: dòng in ra là `replacement`. Sau câu lệnh gán, `e` tham chiếu tới đối tượng mới. Dù hợp lệ về cú pháp, **đánh mất tham chiếu tới ngoại lệ gốc** dễ làm việc chẩn đoán trở nên khó khăn. Thông thường nên giữ biến bắt lỗi ban đầu và tạo một biến khác nếu cần.

Thử thay `catch (IOException e)` bằng `catch (IOException | SQLException e)` trong một `try` có khả năng ném cả hai kiểu thì câu lệnh gán `e = ...` sẽ bị từ chối. Sự khác biệt nằm ở **thể loại tham số bắt ngoại lệ**, không phải ở việc vế phải của phép gán là `IOException`.

## 4. `final` của biến tham chiếu không đồng nghĩa đối tượng bất biến

Ví dụ:

~~~java
try {
    mayFail();
} catch (java.io.IOException | java.sql.SQLException e) {
    System.out.println(e.getClass().getSimpleName());
    System.out.println(e.getMessage());
    throw e;
}
~~~

Đây là phương thức minh họa, trong đó `mayFail()` có thể ném hai ngoại lệ và phương thức bao quanh cần đáp ứng hợp đồng `throws`. `getClass()`, `getMessage()` và `throw e` đều dùng chính đối tượng ngoại lệ ban đầu; không gán lại biến `e`.

Ngược lại, lệnh:

~~~java
e = new java.io.IOException("another");
~~~

thay giá trị tham chiếu của biến `e`, vì vậy không được phép trong multi-catch.

Điều `final` **không bảo đảm**:

- Không bảo đảm đối tượng ngoại lệ không có trạng thái có thể thay đổi.
- Không bảo đảm không có code nào khác đang giữ tham chiếu tới cùng đối tượng.
- Không bảo đảm ngoại lệ không thể được truyền, ném lại hoặc bọc làm cause.
- Không tạo bản sao bất biến của `Throwable`.

Nếu muốn thêm thông tin nghiệp vụ, cách rõ ràng là **tạo một ngoại lệ bọc mới** và giữ đối tượng gốc làm cause:

~~~java
catch (java.io.IOException | java.sql.SQLException e) {
    throw new IllegalStateException(
            "Không thể hoàn thành thao tác dữ liệu", e);
}
~~~

Đây là đoạn minh họa dùng trong một `try` có thể ném hai kiểu lỗi. `e` không bị gán lại; constructor của `IllegalStateException` giữ được nguyên nhân bằng `getCause()`.

## 5. Kiểu tĩnh của tham số khi gộp nhiều ngoại lệ

Trong single-catch:

~~~java
catch (java.sql.SQLException e) {
    System.out.println(e.getSQLState());
}
~~~

biến `e` được khai báo với kiểu `SQLException`, nên có thể gọi `getSQLState()`.

Nhưng khi viết:

~~~java
catch (java.io.IOException | java.sql.SQLException e) {
    System.out.println(e.getMessage()); // Hợp lệ: API chung.
    // System.out.println(e.getSQLState()); // Không hợp lệ.
}
~~~

`e` có kiểu được Java tính từ **least upper bound** (LUB) của các alternative, đôi khi là dạng kiểu giao (*intersection type*). Nó không được xem là `SQLException` một cách vô điều kiện, vì tại runtime `e` cũng có thể tham chiếu tới `IOException`.

Vì vậy, bên trong multi-catch, chỉ nên dựa vào **những phương thức có thể sử dụng an toàn trên kiểu tĩnh** mà compiler xác định, ví dụ `getMessage()`, `getCause()`, `getClass()`, `printStackTrace()`.

Nếu cần API riêng của một loại lỗi, có hai cách.

**Cách A — tách catch theo loại:**

~~~java
try {
    mayFail();
} catch (java.sql.SQLException e) {
    System.out.println(e.getSQLState());
} catch (java.io.IOException e) {
    System.out.println(e.getMessage());
}
~~~

**Cách B — kiểm tra subtype trong multi-catch:**

~~~java
try {
    mayFail();
} catch (java.io.IOException | java.sql.SQLException e) {
    if (e instanceof java.sql.SQLException sqlError) {
        System.out.println(sqlError.getSQLState());
    } else {
        System.out.println(e.getMessage());
    }
}
~~~

Các ví dụ giả định `mayFail()` có khai báo checked exception tương ứng. Cú pháp `instanceof` với pattern variable `sqlError` được Java 17 hỗ trợ; nó tạo một **biến riêng** có kiểu `SQLException` trong phạm vi thích hợp, không gán lại `e`.

Không nên ép kiểu không kiểm tra `(SQLException) e` rồi gọi phương thức chuyên biệt: khi đối tượng thực tế là `IOException`, sẽ phát sinh `ClassCastException`.

## 6. Không được gộp kiểu cha và kiểu con cùng một multi-catch

Cú pháp `catch (FileNotFoundException | IOException ex)` là **lỗi biên dịch** vì `FileNotFoundException extends IOException`. Nếu mọi lỗi I/O có chung cách xử lý, dùng một `catch (IOException ex)`; nếu khác nhau, dùng hai handler với `FileNotFoundException` **trước** `IOException`. Compiler cũng kiểm tra khả năng phát sinh checked exceptions ở `try`.

Quy tắc này **độc lập** với trọng tâm của bài: kể cả khi hai alternative hoàn toàn hợp lệ, tham số chung của multi-catch vẫn **ngầm `final`**, không được gán lại. Việc sửa danh sách alternative không biến `ex` thành biến có thể gán như single-catch.

## 7. Multi-catch và precise rethrow

`throw ex;` trong multi-catch **ném lại đúng đối tượng đang bắt**. Với ví dụ sau, chữ ký giữ hai loại checked exception cụ thể, còn tham số `ex` không thể bị gán lại:

~~~java
static void relay() throws java.io.IOException, java.sql.SQLException {
    try {
        perform();
    } catch (java.io.IOException | java.sql.SQLException ex) {
        throw ex;
    }
}
~~~

Đây là phương thức minh họa; `perform()` cần khai báo có thể ném `IOException` và `SQLException`. **Precise rethrow** là quy tắc compiler có thể suy luận những checked exception cụ thể thoát ra từ `try` khi `throw ex` và tham số `catch` `final` hoặc *effectively final*.

Đừng nhầm: **multi-catch ngầm `final`** là quy tắc cấm gán lại tham số; **precise rethrow** là quy tắc kiểm tra ngoại lệ khi ném lại. Chúng bổ trợ nhau nhưng không đồng nhất. Ngay cả `catch (Exception ex)` đơn, nếu `ex` không bị gán lại, cũng có thể được suy luận hẹp. Nếu tham số single-catch bị gán lại, điều kiện precise rethrow có thể mất. Việc ném lại không thay đổi lớp runtime, không tự phục hồi thất bại và vẫn phải đáp ứng `throws` của tầng gọi.

## 8. Ví dụ Java 17+ hoàn chỉnh

Chương trình minh họa:

1. Một `try` có thể ném hai kiểu checked exception khác nhau.
2. Tham số `catch (IOException | SQLException e)` được dùng để log rồi **ném lại** mà không gán lại.
3. `main` bắt riêng từng kiểu sau khi nhận ngoại lệ từ phương thức trung gian.
4. Nhánh không lỗi không đi vào `catch`.

Tạo tệp **`MultiCatchFinalDemo.java`**:

~~~java
import java.io.IOException;
import java.sql.SQLException;

public class MultiCatchFinalDemo {
    public static void main(String[] args) {
        for (String source : new String[] {"ok", "file", "database"}) {
            try {
                relay(source);
            } catch (IOException e) {
                System.out.println("main IO: " + e.getMessage());
            } catch (SQLException e) {
                System.out.println("main SQL: " + e.getMessage());
            }
        }
    }

    static void relay(String source)
            throws IOException, SQLException {
        try {
            perform(source);
        } catch (IOException | SQLException problem) {
            System.out.println(
                    "relay caught: " + problem.getClass().getSimpleName());
            throw problem;
        }
    }

    static void perform(String source)
            throws IOException, SQLException {
        if ("file".equals(source)) {
            throw new IOException("file unavailable");
        }
        if ("database".equals(source)) {
            throw new SQLException("query unavailable");
        }
        System.out.println("operation ok");
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac MultiCatchFinalDemo.java
java MultiCatchFinalDemo
~~~

**Stdout chính xác:**

~~~text
operation ok
relay caught: IOException
main IO: file unavailable
relay caught: SQLException
main SQL: query unavailable
~~~

### Lần theo luồng thực thi

- Với `"ok"`, `perform` in `operation ok` và kết thúc bình thường. `relay` không đi vào `catch`, vì không có ngoại lệ để bắt.
- Với `"file"`, `perform` ném `IOException`. `catch` trong `relay` nhận nó, in tên kiểu, rồi `throw problem;` ném lại đúng đối tượng. `main` nhận ở nhánh `catch (IOException e)`.
- Với `"database"`, đối tượng được ném là `SQLException`. Cùng khối multi-catch bên trong xử lý bước ghi nhận và ném lại; `main` bắt ở nhánh `catch (SQLException e)`.
- Ở cả hai đường lỗi, không có lệnh `problem = ...;`. Tham số multi-catch vẫn tham chiếu tới cùng ngoại lệ runtime được truyền vào.

**Tại sao `relay` không cần khai báo `throws Exception`?** Vì khai báo `throws IOException, SQLException` tương thích với hai loại ngoại lệ checked cụ thể có thể được chuyển tiếp. Việc xử lý gộp không làm “biến” chúng thành một đối tượng `Exception` mới.

### Thử thay đổi để thấy lỗi biên dịch

Trong mã trên, nếu thêm dòng:

~~~java
problem = new IOException("replacement");
~~~

vào khối `catch (IOException | SQLException problem)`, trình biên dịch sẽ từ chối **ngay tại phép gán** vì `problem` là tham số multi-catch ngầm định `final`. Đây là một đoạn phản ví dụ, không phải phần của chương trình chạy được.

Nếu nhu cầu thực sự là bọc lỗi với thông điệp ở tầng cao hơn, có thể giữ nguyên `problem`:

~~~java
catch (IOException | SQLException problem) {
    throw new IllegalStateException(
            "Không thể hoàn tất tải dữ liệu", problem);
}
~~~

Đây là đoạn minh họa về cách đổi chính sách lỗi: `IllegalStateException` là unchecked; cause giữ ngoại lệ gốc. Nếu API phải chuyển tiếp đúng hai loại checked exception, hãy tiếp tục dùng `throw problem;` như chương trình chính.

## 9. Tại sao tham số multi-catch cần ngầm định `final`?

Quy tắc giúp **một biến bắt lỗi đại diện ổn định cho chính ngoại lệ được bắt** trong suốt khối multi-catch. Khi một biến có thể đại diện cho nhiều kiểu checked exception, việc cho gán một ngoại lệ mới vào cùng biến sẽ tạo nhầm lẫn giữa:

- **Ngoại lệ gốc đã được ném từ khối `try`**; và
- **Ngoại lệ mới được tạo bên trong `catch`**.

Ví dụ ý tưởng không hợp lệ:

~~~java
catch (IOException | SQLException e) {
    e = new IOException("Thay thế lỗi đã nhận");
    throw e;
}
~~~

Ở mức ý nghĩa, đoạn này làm mất tham chiếu tên `e` đến đối tượng gốc. Ở mức luật Java, nó đơn giản là **không biên dịch**. Nếu muốn đổi đối tượng lỗi, hãy viết tên khác:

~~~java
catch (IOException | SQLException e) {
    IllegalStateException wrapped =
            new IllegalStateException("Thao tác thất bại", e);
    throw wrapped;
}
~~~

Bằng cách đó, `e` tiếp tục chỉ tới lỗi gốc; `wrapped` chỉ tới lỗi mới và `wrapped.getCause() == e` là `true`. Mã thể hiện rõ rằng đang bọc lỗi thay vì thay đổi tham số bắt lỗi.

Quy tắc ngầm định `final` giúp hỗ trợ mô hình phân tích kiểu cho multi-catch, đồng thời làm mã dễ theo dõi hơn. Tuy nhiên, **không có nghĩa mọi single-catch đều nên gán lại tham số chỉ vì Java cho phép**. Giữ nguyên đối tượng gốc thường là thực hành tốt cho cả hai loại catch.

## 10. Phạm vi tham số và tác động tới xử lý ngoại lệ

Tên tham số `e` chỉ có hiệu lực trong khối `catch`:

~~~java
try {
    perform("file");
} catch (IOException | SQLException e) {
    System.out.println(e.getMessage());
}
// System.out.println(e); // Sai: e ở ngoài phạm vi catch.
~~~

Đây là đoạn minh họa cần nằm trong một phương thức phù hợp. Không thể truy cập `e` sau dấu `}` kết thúc `catch`.

`final` không mở rộng phạm vi biến. Nó chỉ hạn chế **phép gán lại biến tham số** khi biến còn trong phạm vi.

Nếu cần một kết quả bên ngoài `catch`, phải thiết kế biến kết quả, giá trị trả về hoặc ngoại lệ chuyển tiếp sao cho hợp lệ theo quy tắc biến cục bộ và luồng điều khiển. Không nên đưa một biến ngoại lệ chưa được khởi tạo ra ngoài rồi tìm cách gán trong mọi nhánh chỉ để quan sát nó.

Ví dụ rõ ràng hơn cho mã gọi:

~~~java
static boolean completed(String source) {
    try {
        perform(source);
        return true;
    } catch (IOException | SQLException e) {
        return false;
    }
}
~~~

Đây là phương thức minh họa. `return true` chỉ chạy khi `perform` hoàn tất; đường ném ngoại lệ phù hợp đi vào `catch` rồi trả `false`. Không cần gán lại `e` hay truy cập nó ngoài phạm vi.

Nếu cần đưa ra lý do lỗi, một kết quả có cấu trúc hoặc ngoại lệ chuyển tiếp thường có ích hơn boolean.

## 11. Trường hợp nên và không nên dùng multi-catch

Multi-catch phù hợp khi hai loại lỗi **có cùng chính sách xử lý**:

~~~java
try {
    perform("file");
} catch (IOException | SQLException e) {
    recordFailure(e);
    throw new IllegalStateException("Không thể xử lý", e);
}
~~~

Đây là đoạn minh họa có phương thức `recordFailure(Throwable)`. Cùng chính sách: ghi nhận lỗi và chuyển nó thành lỗi ở tầng cao hơn.

Nếu mỗi loại lỗi cần hướng phục hồi khác nhau, dùng catch riêng có thể thích hợp hơn:

~~~java
try {
    perform("file");
} catch (IOException e) {
    System.out.println("Yêu cầu chọn nguồn dữ liệu khác");
} catch (SQLException e) {
    System.out.println("Yêu cầu kiểm tra kết nối cơ sở dữ liệu");
}
~~~

Hai trường hợp cần phân biệt:

| Tình huống | Cách viết thích hợp |
| --- | --- |
| Hai ngoại lệ có cùng kết quả xử lý, thông điệp và cách chuyển tiếp | `catch (A | B e)` |
| Phải dùng API chuyên biệt từng loại lỗi | Hai `catch` hoặc `instanceof` có kiểm tra |
| Muốn ném lại cùng ngoại lệ đã bắt | `throw e;`; multi-catch hỗ trợ precise rethrow |
| Muốn tạo lỗi mới, giữ lỗi gốc | `throw new SomeException(message, e);` |
| Muốn gán lại biến `e` | Multi-catch **không cho phép**; tạo biến mới nếu cần |
| Hai alternative có quan hệ cha–con | Không dùng cùng một multi-catch |

Multi-catch chỉ là **cách gộp xử lý ngoại lệ**, không tự quyết định ngoại lệ có thể phục hồi được hay không. Kiểu checked/unchecked cũng vẫn tuân theo phân cấp lớp gốc của đối tượng ngoại lệ.

## 12. Những lỗi thường gặp

### Lỗi 1 — Cố gán lại tham số multi-catch

~~~java
catch (IOException | SQLException e) {
    e = new IOException("new");
}
~~~

Không biên dịch: `e` ngầm định `final`. Giải pháp là **không gán lại**. Để đổi lỗi, tạo đối tượng mới vào một biến khác hoặc ném đối tượng bọc với `cause`.

### Lỗi 2 — Nhầm `final` với việc cấm ném lại

~~~java
catch (IOException | SQLException e) {
    throw e;
}
~~~

Hoàn toàn có thể hợp lệ nếu hợp đồng checked exception của phương thức bao quanh cho phép. `throw e` không phải phép gán lại `e`; nó dùng tham chiếu hiện tại để ném.

### Lỗi 3 — Nghĩ multi-catch biến mọi lỗi thành `Exception`

Không. Đối tượng runtime vẫn là `IOException` hoặc `SQLException`. Gộp `catch` không xóa loại cụ thể trong `e.getClass()`, `cause` hoặc stack trace.

### Lỗi 4 — Gọi API chỉ có ở một alternative

~~~java
catch (IOException | SQLException e) {
    // e.getSQLState(); // Sai về kiểu tĩnh.
}
~~~

Không thể giả định mọi đối tượng trong multi-catch đều có API của `SQLException`. Tách catch hoặc kiểm tra `instanceof SQLException sql`.

### Lỗi 5 — Gộp cha và con

`catch (FileNotFoundException | IOException e)` không hợp lệ. `FileNotFoundException` đã thuộc nhóm `IOException`.

### Lỗi 6 — Nghĩ single-catch cũng mặc nhiên final

`catch (IOException e)` thông thường có thể gán lại `e` nếu không khai báo `final`. Nhưng sau khi gán lại, `e` có thể mất tính effectively final, ảnh hưởng precise rethrow và làm mất liên kết tới lỗi gốc.

### Lỗi 7 — Nhầm “effectively final” với “được khai báo final”

Một biến **effectively final** không có từ khóa `final` nhưng trong thực tế không bị gán lại sau khi được khởi tạo. Single-catch có thể đạt điều kiện đó. Multi-catch là trường hợp **ngôn ngữ bắt buộc final ngầm định**. Hai khái niệm liên quan nhưng không giống nhau.

### Lỗi 8 — Nghĩ catch gộp bỏ qua checked exception

`IOException` và `SQLException` vẫn là checked. Khi chuyển tiếp `throw e;`, phương thức phải xử lý hoặc khai báo đúng các loại checked exception có thể thoát ra. Multi-catch không miễn quy tắc biên dịch.

### Lỗi 9 — Gán lại để “đổi thông báo” thay vì bọc lỗi

Thay tham chiếu `e` có thể làm mất cause, stack trace và thông điệp gốc. Với multi-catch, phép gán bị chặn; dùng `new IllegalStateException("...", e)` khi chính sách API muốn bọc lỗi và bảo toàn nguyên nhân.

### Lỗi 10 — Bắt quá rộng chỉ để tránh viết nhiều tên ngoại lệ

`catch (Exception e)` có thể che các lỗi lập trình unchecked ngoài dự kiến. Multi-catch thường diễn đạt tốt hơn ý định **xử lý đúng một tập hợp loại lỗi**; tuy nhiên khi một loại lỗi không còn cần cùng chính sách, đừng cố giữ chung cho ngắn mã.

## 13. Tự kiểm tra

1. Vì sao `catch (IOException | SQLException e)` không cho phép `e = new IOException(...)` dù đối tượng mới thuộc một trong hai kiểu?
2. Tham số `e` của `catch (IOException e)` có ngầm định `final` không? Từ khóa nào có thể dùng nếu muốn cấm gán lại tường minh?
3. `throw e;` có thay đổi giá trị tham chiếu của `e` không? Vì sao lệnh đó không vi phạm `final`?
4. Tại sao `e.getSQLState()` không được phép trực tiếp trong multi-catch `IOException | SQLException`?
5. Nếu cần đọc SQL state, bạn có thể làm gì mà vẫn giữ multi-catch?
6. Vì sao `catch (FileNotFoundException | IOException e)` không hợp lệ?
7. Precise rethrow giúp giữ chữ ký `throws` hẹp như thế nào?
8. Một single-catch `catch (Exception e)` không gán lại `e` có thể được precise rethrow không?
9. Nếu single-catch `catch (Exception e)` gán `e = new Exception(...)` rồi `throw e;`, compiler còn có thể suy ra chính xác hai loại checked ban đầu không?
10. `final` ngầm định có khiến đối tượng ngoại lệ bất biến không?
11. Nếu muốn thêm ngữ cảnh mà vẫn giữ nguyên nhân, nên làm gì thay vì gán lại `e`?
12. Nếu `perform("ok")` không ném lỗi, khối multi-catch có chạy không?
13. Đối tượng bị bắt là `SQLException` nhưng tham số `e` có nhiều kiểu alternative; `e.getClass().getSimpleName()` cho biết kiểu nào?
14. Khi hai ngoại lệ cần hai phương án khôi phục khác nhau, có nhất thiết gộp vào multi-catch không?

**Đáp án tóm tắt:** (1) Tham số multi-catch ngầm định `final`; (2) không, có thể thêm `final`; (3) không, chỉ ném tham chiếu đã có; (4) kiểu tĩnh phải an toàn với cả hai alternative; (5) `instanceof SQLException sql` hoặc tách catch; (6) kiểu cha–con không được đồng xuất hiện; (7) compiler giới hạn loại ngoại lệ có thể ném lại; (8) có khi thỏa quy tắc effectively final và phân tích nguồn exception; (9) không, biến đã bị gán lại; (10) không; (11) tạo ngoại lệ bọc với cause; (12) không; (13) `SQLException`; (14) không, tách catch có thể rõ ràng hơn.

## 14. Ghi nhớ

**Tham số của `catch (A | B e)` là `final` ngầm định**: Java không cho phép gán lại `e`, nhưng vẫn cho đọc, truyền, ghi nhận và ném lại đối tượng ngoại lệ. Các đối tượng được bắt vẫn giữ kiểu runtime ban đầu, còn kiểu tĩnh của tham số chỉ cho phép những thao tác an toàn với các alternative. Khi cần chuyển đổi lỗi, hãy tạo ngoại lệ mới và giữ `e` làm cause; khi cần chuyển tiếp, `throw e;` cùng cơ chế **precise rethrow** giúp giữ hợp đồng checked exception rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multi-catch-rethrow-005">Giới hạn thành viên được truy cập qua tham số multi-catch</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn thành viên được truy cập qua tham số multi-catch trong Java

## 1. Mục tiêu học tập

Multi-catch cho phép xử lý nhiều loại ngoại lệ trong **một** khối `catch`:

```java
try {
    performOperation();
} catch (java.io.IOException | java.sql.SQLException failure) {
    System.out.println(failure.getMessage());
}
```

Đây là đoạn minh họa bên trong một phương thức; `performOperation()` cần được định nghĩa và có khả năng ném các kiểu checked exception được bắt. Điểm quan trọng: dù tại thời điểm chạy `failure` là một đối tượng `IOException` **hoặc** `SQLException`, trình biên dịch không tự cho phép mọi phương thức riêng của hai lớp đó trên cùng biến `failure`.

Sau bài này, bạn có thể:

- Hiểu kiểu của tham số ngoại lệ trong `catch (A | B e)` được xác định theo **least upper bound** (giới hạn trên chung gần nhất theo quy tắc hệ thống kiểu Java).
- Phân biệt **kiểu thực tế của đối tượng tại runtime** với **kiểu tĩnh của biểu thức `e` tại compile time**.
- Nhận biết các thành viên được truy cập trực tiếp qua `e`, các thành viên riêng của từng nhánh và quy tắc access modifier.
- Dùng `instanceof` hoặc pattern matching cho `instanceof` trong Java 17 để truy cập API riêng sau khi kiểm tra kiểu.
- Hiểu vì sao không thể gán lại tham số multi-catch và cách rethrow giữ thông tin ngoại lệ.
- Nhận biết lỗi thường gặp với kiểu ngoại lệ cha–con trong multi-catch, phương thức overload và checked exception.
- Viết các chương trình Java 17+ biên dịch được với kết quả quan sát xác định.

Trọng tâm bài là **giới hạn thành viên có thể truy cập qua tham số multi-catch**. Các quy tắc liên quan đến rethrow, checked exception và phạm vi biến được trình bày để giải thích đầy đủ cơ chế này.

## 2. Multi-catch khác nhiều `catch` riêng ở điểm nào?

Cách viết hai handler có cùng logic:

```java
try {
    readOrQuery();
} catch (java.io.IOException failure) {
    System.out.println(failure.getMessage());
} catch (java.sql.SQLException failure) {
    System.out.println(failure.getMessage());
}
```

Có thể gộp thành:

```java
try {
    readOrQuery();
} catch (java.io.IOException | java.sql.SQLException failure) {
    System.out.println(failure.getMessage());
}
```

Cả hai là **mảnh mã minh họa**, đòi hỏi `readOrQuery()` phù hợp. Dấu `|` ở đây là cú pháp **multi-catch** (Java 7+), không phải toán tử OR logic trên biến và không tạo một kiểu union thông thường để sử dụng ở mọi nơi trong Java.

Ở cách thứ nhất, mỗi handler có một tham số có kiểu cụ thể: `IOException` hoặc `SQLException`. Ở cách thứ hai, **chỉ có một biến `failure`** trong thân handler. Biến đó phải dùng an toàn cho mọi kiểu mà handler có thể nhận.

### Không thể suy ra “cùng tên biến” nghĩa là “cùng kiểu cụ thể”

`IOException` và `SQLException` đều kế thừa `Exception`, nhưng có những phương thức riêng:

- `SQLException.getSQLState()` trả mã SQLSTATE, nếu có.
- `SQLException.getErrorCode()` trả mã lỗi đặc thù nhà cung cấp.
- `SQLException.getNextException()` theo chuỗi lỗi SQL.
- `IOException` không khai báo các phương thức `getSQLState()`, `getErrorCode()` hay `getNextException()`.

Do đó, câu lệnh `failure.getMessage()` hợp lệ trong multi-catch vì `getMessage()` thuộc `Throwable`, còn `failure.getSQLState()` không hợp lệ nếu chưa thu hẹp kiểu.

## 3. Kiểu tĩnh của tham số multi-catch

Với cú pháp:

```java
catch (TypeA | TypeB | TypeC ex) {
    // Trình biên dịch phải xác định kiểu của ex.
}
```

Java xác định kiểu của tham số theo phép **least upper bound** — thường viết `lub(TypeA, TypeB, TypeC)`. Ý tưởng là tìm giới hạn kiểu chung đủ để biểu diễn an toàn tất cả các lựa chọn ngoại lệ.

Trong ví dụ:

```java
catch (java.io.IOException | java.sql.SQLException ex) {
    System.out.println(ex.getClass().getSimpleName());
    System.out.println(ex.getMessage());
    System.out.println(ex.getCause());
}
```

Tham số `ex` có thể trỏ đến một đối tượng của bất kỳ lựa chọn nào tại runtime. Những phương thức ở trên hợp lệ vì đều là thành viên của `Object` hoặc `Throwable` trên hệ phân cấp chung.

**Chú ý về độ chính xác:** `lub` trong Java có thể là kiểu giao (*intersection type*) gồm một lớp chung và những interface phù hợp. Vì thế không nên khẳng định **mọi** multi-catch đều có kiểu tĩnh chính xác là `Exception`. Đối với việc gọi thành viên, hãy xem thành viên đó có mặt trong kiểu tĩnh mà compiler suy ra và có **truy cập được** tại vị trí gọi hay không.

Một mô hình tư duy hữu ích:

```text
IOException -----\
                   > tham số ex của catch
SQLException ----/   |
                       v
          các thành viên hợp lệ từ giới hạn kiểu chung
          (vd. getMessage(), getCause(), printStackTrace())
```

**Không phải:** trình biên dịch chấp nhận mọi thành viên của `IOException` **cộng với** mọi thành viên của `SQLException`. Điều đó sẽ không an toàn vì lúc chạy đối tượng có thể chỉ thuộc một nhánh.

### 3.1. Runtime type vẫn được giữ nguyên

Dù biểu thức `ex` có kiểu tĩnh chung, đối tượng mà nó tham chiếu **không bị chuyển đổi** thành một đối tượng `Exception` mới. Nếu phương thức ném `SQLException`, đối tượng thực sự vẫn là `SQLException`. Các thông tin như message, cause, SQL state và stack trace của đối tượng không tự mất đi.

Truy cập phương thức riêng bị hạn chế bởi **kiểm tra kiểu khi biên dịch**. Bạn có thể truy cập sau khi chứng minh được kiểu thực tế, như ví dụ `instanceof` ở phần sau.

### 3.2. Kiểu chung không đồng nghĩa chỉ có phương thức của `Object`

Nếu các lựa chọn kế thừa cùng một **lớp ngoại lệ trung gian** có phương thức ứng dụng tự định nghĩa, kiểu chung có thể cho phép truy cập phương thức ấy.

Ví dụ:

```text
Exception
    └── AppFailure (method: code())
        ├── NetworkFailure
        └── ValidationFailure
```

Trong `catch (NetworkFailure | ValidationFailure e)`, một phương thức `code()` public khai báo tại `AppFailure` có thể gọi trực tiếp bằng `e.code()`. Chúng ta sẽ kiểm chứng bằng chương trình độc lập ở mục 6.

## 4. Ví dụ chạy được: thành viên chung và phương thức riêng

Tạo tệp **`MultiCatchAccessDemo.java`**:

```java
import java.io.IOException;
import java.sql.SQLException;

public class MultiCatchAccessDemo {
    private static void execute(String mode)
            throws IOException, SQLException {
        if ("io".equals(mode)) {
            throw new IOException("disk unavailable");
        }
        if ("sql".equals(mode)) {
            throw new SQLException("connection refused", "08006");
        }
    }

    private static void run(String mode) {
        try {
            execute(mode);
            System.out.println(mode + ": success");
        } catch (IOException | SQLException failure) {
            System.out.println(failure.getClass().getSimpleName()
                    + ": " + failure.getMessage());

            if (failure instanceof SQLException sqlFailure) {
                System.out.println("SQLSTATE=" + sqlFailure.getSQLState());
            }
        }
    }

    public static void main(String[] args) {
        run("io");
        run("sql");
        run("ok");
    }
}
```

Lệnh biên dịch/chạy với JDK 17+:

```bash
javac MultiCatchAccessDemo.java
java MultiCatchAccessDemo
```

Kết quả `stdout`:

```text
IOException: disk unavailable
SQLException: connection refused
SQLSTATE=08006
ok: success
```

Giải thích từng dòng:

1. `run("io")` nhận `IOException`; `getClass()` và `getMessage()` hợp lệ qua tham số multi-catch vì thuộc API chung.
2. `failure instanceof SQLException sqlFailure` là `false`, nên không in SQLSTATE.
3. `run("sql")` nhận `SQLException`; đối tượng vẫn giữ dữ liệu `SQLState` được truyền khi khởi tạo.
4. Mẫu `instanceof SQLException sqlFailure` kiểm tra runtime type và tạo biến `sqlFailure` có kiểu tĩnh cụ thể là `SQLException` ở nhánh đúng, nhờ đó `sqlFailure.getSQLState()` hợp lệ.
5. `run("ok")` không ném ngoại lệ, đi theo đường thành công.

Dạng `instanceof` với binding variable (`pattern matching for instanceof`) là tính năng chính thức trong Java 17, không cần bật preview.

## 5. Ví dụ **không biên dịch**: truy cập phương thức riêng trực tiếp

Đoạn sau có lỗi cố ý:

```java
import java.io.IOException;
import java.sql.SQLException;

public class InvalidMultiCatchMember {
    static void fail(boolean sql) throws IOException, SQLException {
        if (sql) {
            throw new SQLException("db error", "42000");
        }
        throw new IOException("IO error");
    }

    public static void main(String[] args) {
        try {
            fail(true);
        } catch (IOException | SQLException failure) {
            System.out.println(failure.getSQLState()); // LỖI BIÊN DỊCH
        }
    }
}
```

Nếu lưu thành `InvalidMultiCatchMember.java` và chạy:

```bash
javac InvalidMultiCatchMember.java
```

Compiler sẽ báo không tìm được phương thức phù hợp `getSQLState()` trên kiểu tĩnh của biểu thức `failure`. Văn bản chẩn đoán cụ thể có thể thay đổi theo bản JDK; **không chạy `java InvalidMultiCatchMember`** vì ví dụ này chủ ý không biên dịch.

**Tại sao `fail(true)` vẫn không giúp?** Trình biên dịch kiểm tra thân `catch` theo **kiểu khai báo của tham số**, không suy luận từ giá trị `true` của một lần gọi để biến `failure` thành `SQLException`. Hơn nữa, chữ ký `fail` có khả năng truyền hai loại checked exception.

Sửa bằng cách kiểm tra kiểu:

```java
if (failure instanceof SQLException sqlFailure) {
    System.out.println(sqlFailure.getSQLState());
}
```

Đây là **mảnh mã trong thân handler**, không phải chương trình độc lập. Việc thêm `instanceof` chuyển truy cập API riêng sang nhánh đã có bằng chứng kiểu tương thích.

### 5.1. Cũng không thể truyền tham số chung vào hàm chỉ nhận một loại

Ví dụ giả sử có phương thức:

```java
static void logSql(java.sql.SQLException failure) {
    System.out.println(failure.getSQLState());
}
```

Đoạn sau **không hợp lệ**:

```java
catch (java.io.IOException | java.sql.SQLException failure) {
    logSql(failure); // Không đảm bảo failure là SQLException.
}
```

Đây là đoạn đặt trong một chuỗi `try/catch` có các kiểu checked exception phù hợp. `logSql(SQLException)` đòi hỏi đối số có kiểu tương thích với `SQLException`, trong khi `failure` có thể là `IOException`.

Sửa:

```java
catch (java.io.IOException | java.sql.SQLException failure) {
    if (failure instanceof java.sql.SQLException sqlFailure) {
        logSql(sqlFailure);
    } else {
        System.out.println("Not a SQL exception");
    }
}
```

**Quy tắc áp dụng cho cả tham số hàm**, phép gán và chọn overload. Không thể dùng trực tiếp biến multi-catch như thể nó luôn thuộc một nhánh cụ thể.

## 6. Ví dụ chạy được: cùng lớp cha tự định nghĩa

Để thấy kiểu chung không chỉ dừng ở `Throwable`, hãy tạo hai ngoại lệ khác nhau có cùng API chung.

Tạo tệp **`CommonCatchMemberDemo.java`**:

```java
abstract class AppFailure extends Exception {
    private final String code;

    protected AppFailure(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

class NetworkFailure extends AppFailure {
    NetworkFailure() {
        super("NETWORK");
    }
}

class ValidationFailure extends AppFailure {
    ValidationFailure() {
        super("VALIDATION");
    }
}

public class CommonCatchMemberDemo {
    private static void fail(boolean network)
            throws NetworkFailure, ValidationFailure {
        if (network) {
            throw new NetworkFailure();
        }
        throw new ValidationFailure();
    }

    private static void inspect(boolean network) {
        try {
            fail(network);
        } catch (NetworkFailure | ValidationFailure failure) {
            System.out.println(failure.code()
                    + ": " + failure.getMessage());
        }
    }

    public static void main(String[] args) {
        inspect(true);
        inspect(false);
    }
}
```

```bash
javac CommonCatchMemberDemo.java
java CommonCatchMemberDemo
```

Kết quả:

```text
NETWORK: NETWORK
VALIDATION: VALIDATION
```

`NetworkFailure` và `ValidationFailure` đều kế thừa `AppFailure`; tại điểm `catch`, tham số có thể dùng phương thức public `code()` của lớp cha chung. Không cần ép kiểu về từng lớp con khi chỉ truy cập thông tin mà cả hai **đều cam kết có**.

Chương trình được chủ động thiết kế đơn giản để chỉ tập trung vào vấn đề thành viên chung. Trong hệ thống thật, hãy cân nhắc ngoại lệ ứng dụng có nên công bố một lớp cha dùng chung hoặc một hợp đồng interface ổn định để handler xử lý nhất quán hay không.

### 6.1. Interface chung và kiểu giao

Least upper bound trong hệ thống kiểu Java có thể chứa cả các interface mà những lựa chọn ngoại lệ cùng thực thi. Khi đó, các thành viên interface **có thể** được truy cập từ tham số multi-catch nếu Java suy ra kiểu giao tương thích và quy tắc truy cập cho phép.

Tuy nhiên, việc một interface có cùng **tên phương thức** nhưng không thật sự là interface chung của các lớp không đủ để đảm bảo compiler cho phép gọi. Đừng dựa trên “duck typing” (hai lớp tình cờ có phương thức cùng tên): Java kiểm tra kiểu tĩnh, hệ kế thừa và interface.

Trong API ứng dụng, **lớp cha chung có hợp đồng rõ ràng** như `AppFailure` thường là ví dụ dễ đọc nhất. Với kiểu giao phức tạp/generics, nên kiểm tra kết quả biên dịch cụ thể của mã đang dùng.

## 7. Kiểu tĩnh quyết định việc chọn phương thức overload

Ngay cả khi mọi đối tượng lúc chạy đều có một kiểu cụ thể, Java quyết định **overload** tại compile time dựa trên kiểu của biểu thức truyền vào.

Giả sử có hai overload:

```java
static void report(Exception failure) {
    System.out.println("general");
}

static void report(java.sql.SQLException failure) {
    System.out.println("SQL-specific");
}
```

Nếu đặt đoạn sau trong một phương thức thích hợp:

```java
catch (java.io.IOException | java.sql.SQLException failure) {
    report(failure);
}
```

Lời gọi `report(failure)` không được lựa chọn overload nhận `SQLException` chỉ vì tại runtime đối tượng thực tế có thể là `SQLException`. Compiler sẽ chọn overload áp dụng được với **kiểu tĩnh chung** của `failure`; trong ví dụ này overload nhận `Exception` phù hợp.

Để gọi overload chuyên biệt:

```java
if (failure instanceof java.sql.SQLException sqlFailure) {
    report(sqlFailure);
} else {
    report(failure);
}
```

Đây là các mảnh mã minh họa được dùng trong phạm vi xử lý ngoại lệ, không phải chương trình `main` độc lập. Quy tắc chung là **overload resolution** dùng kiểu tĩnh, còn **override dispatch** tại runtime có thể chọn phiên bản ghi đè theo lớp của đối tượng đã được chọn phương thức tương ứng.

## 8. Điều gì xảy ra với các thành viên `protected` và `private`?

Khả năng truy cập qua tham số multi-catch còn chịu quy tắc truy cập thành viên thông thường của Java:

- `public`: có thể gọi nếu thành viên xuất hiện trong kiểu tĩnh phù hợp và các kiểu liên quan truy cập được.
- `protected`: có những quy tắc riêng theo package, kế thừa và kiểu của biểu thức nhận; không tự nhiên thành public chỉ vì hai ngoại lệ đều có thành viên đó.
- Thành viên không ghi access modifier (*package-private*): phụ thuộc package đang truy cập.
- `private`: chỉ truy cập được trong phạm vi được Java cho phép; multi-catch không cấp quyền truy cập ngoại lệ vào dữ liệu riêng của lớp khác.

Ví dụ có hai lớp ngoại lệ khác nhau, mỗi lớp tự khai báo phương thức `detail()`, **nhưng không có lớp cha/interface chung khai báo `detail()`**. Không thể giả định `failure.detail()` hợp lệ chỉ vì hai lớp đều có phương thức cùng tên. Trường hợp đó cần thiết kế API chung hoặc kiểm tra kiểu cụ thể.

Ngoài ra, phương thức có thể được khai báo ở lớp cha chung nhưng `protected`, và mã gọi ở package không thỏa điều kiện bảo vệ thì vẫn không gọi được. Đây là vấn đề **accessibility**, tách biệt với việc tìm được thành viên trong loại tĩnh.

## 9. Tham số multi-catch là implicitly final

Trong `catch (IOException | SQLException failure)`, `failure` **ngầm `final`**. Không thể gán `failure = new IOException("other")`, nhưng vẫn được gọi những phương thức mà kiểu tĩnh chung cho phép, kiểm tra `instanceof` hoặc ném lại chính đối tượng đó. Quy tắc này **không cấp thêm thành viên** cho biến: việc không được gán lại và việc có thể gọi `SQLException.getSQLState()` là hai câu hỏi kiểu học độc lập. Muốn dùng API riêng cho một nhánh, hãy thu hẹp kiểu an toàn hoặc tách handler theo chính sách.

## 10. Rethrow và mối liên hệ với kiểu multi-catch

Một điểm hữu ích của multi-catch là có thể ghi log/đính kèm ngữ cảnh chung rồi `throw failure;`. Khi làm vậy, Java vẫn kiểm tra checked exception mà lời gọi có thể truyền ra.

Tạo file **`MultiCatchRethrowDemo.java`**:

```java
import java.io.IOException;
import java.sql.SQLException;

public class MultiCatchRethrowDemo {
    private static void execute(String mode)
            throws IOException, SQLException {
        if ("io".equals(mode)) {
            throw new IOException("read failed");
        }
        if ("sql".equals(mode)) {
            throw new SQLException("query failed");
        }
    }

    private static void relay(String mode)
            throws IOException, SQLException {
        try {
            execute(mode);
        } catch (IOException | SQLException failure) {
            System.out.println("forward: "
                    + failure.getClass().getSimpleName());
            throw failure;
        }
    }

    public static void main(String[] args) {
        for (String mode : new String[] {"io", "sql", "ok"}) {
            try {
                relay(mode);
                System.out.println(mode + ": success");
            } catch (IOException | SQLException failure) {
                System.out.println("received: "
                        + failure.getClass().getSimpleName());
            }
        }
    }
}
```

```bash
javac MultiCatchRethrowDemo.java
java MultiCatchRethrowDemo
```

`stdout`:

```text
forward: IOException
received: IOException
forward: SQLException
received: SQLException
ok: success
```

`relay` khai báo `throws IOException, SQLException`; khi ném lại `failure`, chính đối tượng đã nhận được truyền lên caller. Java cho phép phân tích chính xác hơn các checked exception có thể được rethrow trong ngữ cảnh bắt ngoại lệ phù hợp, thay vì buộc khai báo `throws Exception` một cách không cần thiết.

**Không lẫn lộn:** kiểu tĩnh chung giới hạn **thành viên bạn được gọi trên `failure`**; quy tắc exception checking khi `throw failure;` có thể xem xét các kiểu ngoại lệ bắt được và đường phát sinh trong `try`. Vì vậy, việc `failure.getSQLState()` bị từ chối không ngụ ý `throw failure;` cũng phải bị từ chối nếu phương thức đã khai báo hai loại checked exception tương ứng.

### 10.1. `catch` một loại và precise rethrow

Java 7 còn có cơ chế phân tích rethrow chính xác cho một số tham số `catch` **effectively final** của handler một kiểu, dựa trên các loại checked exception mà `try` có thể ném. Điều này liên quan nhưng **không đồng nhất** với quy tắc tham số multi-catch **implicitly final**.

Nếu cần sửa một handler khiến precise rethrow không còn áp dụng, xem liệu tham số có bị gán lại hay không và kiểm tra những loại checked exception có thể thoát thực tế từ khối `try`. Hãy tránh đánh đồng “có nhiều kiểu trong `catch`” với “mọi cách rethrow đều có cùng luật”.

### 10.2. Khi nào nên ném lại, khi nào nên bọc?

- **Ném lại** `throw failure;` khi tầng hiện tại không thể phục hồi và hợp đồng của tầng trên vẫn cho phép các kiểu checked exception đó.
- **Bọc** bằng ngoại lệ có `cause` khi tầng hiện tại thêm ý nghĩa miền nghiệp vụ hoặc cần đổi giao diện lỗi.
- **Xử lý tại chỗ** nếu biết cách phục hồi thực sự, không chỉ ghi log rồi giả định thao tác thành công.

Trong mọi trường hợp, đừng viết một handler chỉ để bắt, in stack trace nhiều lần và làm mất đường đi của lỗi.

## 11. Các giới hạn khác của cú pháp multi-catch

### 11.1. Những kiểu thay thế không được có quan hệ cha–con

Đoạn này **không biên dịch**:

```java
catch (java.io.IOException
       | java.io.FileNotFoundException failure) {
    System.out.println(failure.getMessage());
}
```

`FileNotFoundException` là lớp con của `IOException`, nên hai lựa chọn trong **cùng một multi-catch** có quan hệ subtype và bị Java cấm. Thay bằng:

```java
catch (java.io.IOException failure) {
    System.out.println(failure.getMessage());
}
```

Nếu muốn hai cách xử lý khác nhau, dùng hai `catch` liên tiếp theo thứ tự **FileNotFoundException trước, IOException sau**. Đừng dùng multi-catch để liệt kê kiểu rộng và chính lớp con của nó.

### 11.2. Không thể viết multi-catch với danh sách kiểu bất kỳ

Mỗi lựa chọn phải là một kiểu ngoại lệ hợp lệ theo ngữ pháp/các giới hạn của Java, được ngăn cách bằng `|`. Một số ví dụ cú pháp **sai**:

```java
catch (java.io.IOException, java.sql.SQLException e) { } // Sai dấu phẩy.
catch (new java.io.IOException() | java.sql.SQLException e) { } // Sai: đối tượng không phải kiểu.
```

Đây là hai mảnh cố ý không biên dịch. Trong mệnh đề `throws`, các **kiểu** lại được phân cách bằng dấu phẩy: `throws IOException, SQLException`. Không trộn hai vị trí cú pháp này.

### 11.3. Kiểm tra checked exception vẫn áp dụng

Multi-catch không miễn các quy tắc về checked exception. Một handler có thể không hợp lệ nếu `try` không có cách nào ném một kiểu checked exception mà nó khai báo bắt, ngay cả khi kiểu khác là hợp lệ.

Ví dụ đoạn sau **không biên dịch** vì `try` không có thao tác có khả năng phát sinh checked `SQLException`:

```java
try {
    System.out.println("hello");
} catch (java.io.IOException | java.sql.SQLException failure) {
    System.out.println(failure.getMessage());
}
```

Trên thực tế, cả `IOException` và `SQLException` đều không thể phát sinh từ khối `try` đang hiển thị, nên compiler có đủ căn cứ từ chối. Khi kiểm tra một multi-catch cụ thể, hãy phân biệt:
- Các lựa chọn ngoại lệ có quan hệ subtype không hợp lệ.
- Một kiểu checked exception có thể không được ném bởi khối `try`.
- Cố gọi phương thức riêng không có trên kiểu tĩnh của tham số.
- Cố gán lại biến multi-catch implicitly final.

Các lỗi có chẩn đoán khác nhau và cần cách sửa khác nhau.

## 12. Lựa chọn thiết kế: giữ một handler chung hay tách handler?

Không có quy tắc rằng cứ có hai loại ngoại lệ là phải dùng multi-catch. Hãy chọn dựa trên **logic xử lý**:

| Nhu cầu | Cách phù hợp | Vì sao |
| --- | --- | --- |
| Hai lỗi có cùng chính sách ghi nhận và truyền tiếp | Một `catch (A | B e)` | Tránh lặp logic, chỉ dùng API chung |
| Hai loại cần cách phục hồi khác nhau | Hai `catch` riêng | Mỗi handler có tham số kiểu cụ thể |
| Cả hai có một lớp cha ứng dụng với API chung | Multi-catch hoặc bắt lớp cha thích hợp | Truy cập được hợp đồng chung; cân nhắc độ rộng API |
| Chỉ một nhánh cần API riêng | Multi-catch + `instanceof` khi phân nhánh nhỏ | Thu hẹp kiểu an toàn, tránh ép kiểu vô điều kiện |
| Hầu hết logic khác nhau theo từng loại | Hai `catch` riêng | Dễ hiểu hơn chuỗi `instanceof` phức tạp |
| Cần giữ loại checked exception khi truyền lên | Rethrow với hợp đồng `throws` đúng | Không mất đối tượng gốc và thông tin kiểu |
| Muốn đổi tầng trừu tượng lỗi | Tạo ngoại lệ mới kèm `cause` | Thể hiện ngữ cảnh nhưng giữ nguyên nhân |

### 12.1. Tách handler khi cần phương thức chuyên biệt thường xuyên

Ví dụ:

```java
try {
    perform();
} catch (java.sql.SQLException sql) {
    System.err.println("SQLSTATE: " + sql.getSQLState());
    System.err.println("Vendor code: " + sql.getErrorCode());
} catch (java.io.IOException io) {
    System.err.println("File I/O: " + io.getMessage());
}
```

Đây là mảnh minh họa, cần định nghĩa `perform()` có khả năng ném hai loại checked exception. Với xử lý chuyên biệt này, hai handler riêng thường dễ đọc hơn multi-catch rồi nhiều `instanceof`.

### 12.2. Dùng `instanceof` đúng lúc

`instanceof` hữu ích khi chỉ có một phần nhỏ cần xử lý theo kiểu cụ thể; nhưng nếu bạn phải lặp lại kiểm tra `instanceof` hàng chục lần, handler chung có thể đang che khuất hai chính sách xử lý khác nhau. Hãy tách handler nếu chúng cần các thao tác riêng biệt.

Không dùng cast thiếu kiểm tra:

```java
catch (java.io.IOException | java.sql.SQLException failure) {
    java.sql.SQLException sql =
            (java.sql.SQLException) failure; // Có thể ClassCastException khi là IOException.
}
```

Đoạn này có thể **biên dịch** trong một `try/catch` hợp lệ, nhưng cast chỉ an toàn nếu đối tượng thực sự là `SQLException`. Đừng giả định vì một nhánh thử nghiệm hay chạy với SQL mà mọi lần runtime đều đi qua loại đó.

Nên dùng:

```java
if (failure instanceof java.sql.SQLException sql) {
    System.out.println(sql.getSQLState());
}
```

Đây là mảnh trong thân handler; Java 17 hỗ trợ type pattern trong `instanceof` mà không cần cast thêm.

## 13. Bài tập tự kiểm tra

### Bài 1 — Thành viên chung

```java
catch (java.io.IOException | java.sql.SQLException ex) {
    System.out.println(ex.getMessage());
    System.out.println(ex.getCause());
}
```

**Đúng hay sai?** Đúng về truy cập thành viên. Các phương thức này được công bố trong `Throwable` nên hợp lệ qua kiểu tĩnh chung, giả sử câu lệnh `try` tương ứng có thể ném các checked exception đó.

### Bài 2 — Thành viên chỉ thuộc `SQLException`

```java
catch (java.io.IOException | java.sql.SQLException ex) {
    System.out.println(ex.getErrorCode());
}
```

**Đúng hay sai?** Sai về kiểm tra kiểu tại compile time: `getErrorCode()` không thuộc API chung của hai lựa chọn. Kiểm tra `ex instanceof SQLException sql` trước khi gọi `sql.getErrorCode()` hoặc tách handler.

### Bài 3 — Gán lại tham số

```java
catch (java.io.IOException | java.sql.SQLException ex) {
    ex = new java.io.IOException("new");
}
```

**Đúng hay sai?** Sai. Tham số multi-catch implicitly final, không thể gán lại.

### Bài 4 — Lớp cha chung

Giả sử `ReadFailure` và `WriteFailure` đều kế thừa `StorageFailure` với phương thức public `kind()`. Có gọi `ex.kind()` trong `catch (ReadFailure | WriteFailure ex)` không?

**Đáp án:** Có, nếu những khai báo kiểu hợp lệ và `kind()` thực sự truy cập được qua kiểu chung mà Java suy ra.

### Bài 5 — Rethrow

Phương thức `execute()` khai báo `throws IOException, SQLException` và trong `try` chỉ gọi phương thức đó; trong `catch (IOException | SQLException ex)` có `throw ex;`. Phương thức bao ngoài cần làm gì?

**Đáp án:** Bắt hoặc khai báo các checked exception có thể truyền ra, chẳng hạn `throws IOException, SQLException`. Không cần tự mở rộng thành `throws Exception` chỉ vì biểu thức `ex` có kiểu chung.

### Bài 6 — Hai lựa chọn trong một nhánh subtype

`catch (IOException | FileNotFoundException ex)` hợp lệ không?

**Đáp án:** Không. `FileNotFoundException` kế thừa `IOException` nên vi phạm giới hạn của multi-catch.

### Bài 7 — Overload

Có `report(Exception)` và `report(SQLException)`. Trong `catch (IOException | SQLException ex)`, gọi `report(ex)` có tự chọn phiên bản SQLException khi runtime nhận SQLException không?

**Đáp án:** Không. Chọn overload tại compile time theo kiểu tĩnh của `ex`, không dựa vào class thực tế để chuyển sang overload SQL.

## 14. Những nhầm lẫn thường gặp

**“`IOException | SQLException` tạo một đối tượng vừa là IOException vừa là SQLException.”** Sai. Multi-catch nhận một đối tượng thuộc một trong các nhánh ngoại lệ phù hợp.

**“Có thể gọi tổng hợp mọi phương thức của các lớp sau dấu `|`.”** Sai. Trình biên dịch chỉ cho phép thành viên phù hợp với kiểu tĩnh chung và quy tắc truy cập.

**“Compiler biết lần này SQL nên `ex.getSQLState()` luôn hợp lệ.”** Sai. Kiểu tĩnh không tự trở thành `SQLException` vì dữ liệu chạy hoặc nhánh thử nghiệm.

**“`ex.getMessage()` không hợp lệ vì không biết kiểu cụ thể.”** Sai. `getMessage()` thuộc `Throwable` nên dùng được.

**“Trong multi-catch, tham số luôn được khai báo chính xác là `Exception`.”** Sai. Kiểu dùng `lub` và có thể có các thành phần kiểu giao hoặc lớp cha chung khác.

**“Chỉ cần hai lớp có phương thức trùng tên là biến multi-catch gọi được phương thức đó.”** Sai. Java dùng quan hệ kiểu, không dùng cơ chế duck typing.

**“`final` nghĩa là không thể gọi phương thức thay đổi đối tượng ngoại lệ.”** Sai. `final` áp dụng cho phép gán biến tham số, không làm đối tượng bất biến.

**“`throw ex` bắt buộc phải khai báo `throws Exception` vì kiểu tĩnh chung.”** Sai trong nhiều tình huống precise rethrow, khi Java có thể xác định các loại checked exception được bắt và truyền lại.

**“`catch (FileNotFoundException | IOException e)` giúp xử lý riêng hai trường hợp.”** Sai. Đây là hai loại có quan hệ cha–con nên multi-catch không hợp lệ.

**“Ép kiểu `(SQLException) ex` luôn an toàn nếu catch có SQL.”** Sai. Ex có thể là `IOException`, cast có thể ném `ClassCastException`.

**“Muốn dùng API SQL riêng thì luôn phải multi-catch rồi instanceof.”** Sai. Nếu đường phục hồi khác nhau đáng kể, hai `catch` riêng có thể rõ ràng hơn.

## 15. Tài liệu chính thức

- [Java Language Specification 17 — `14.20, The try Statement](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20): cú pháp multi-catch, kiểu của tham số và ràng buộc các lựa chọn.
- [Java Language Specification 17 — `11.2, Compile-Time Checking of Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.2): kiểm tra checked exception và rethrow.
- [Java Language Specification 17 — `4.10.4, Least Upper Bound](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.10.4): cách Java xác định giới hạn kiểu chung.
- [Java Language Specification 17 — `15.20.2, Type Comparison Operator instanceof](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.20.2): kiểm tra và thu hẹp kiểu với `instanceof`.
- [Java SE 17 API — Throwable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html): các thành viên chung như `getMessage()`, `getCause()`.
- [Java SE 17 API — SQLException](https://docs.oracle.com/en/java/javase/17/docs/api/java.sql/java/sql/SQLException.html): các thành viên riêng của ngoại lệ SQL.
- [Java SE 17 API — IOException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/IOException.html): loại ngoại lệ I/O thường được dùng trong multi-catch.

**Ghi nhớ:** `catch (A | B e)` bắt được hai nhánh ngoại lệ, nhưng tham số `e` chỉ được truy cập những thành viên mà **kiểu tĩnh chung suy ra từ các lựa chọn** cho phép. Muốn dùng thành viên chỉ có ở một nhánh, hãy thu hẹp kiểu an toàn bằng `instanceof` hoặc tách các handler khi chính sách xử lý khác nhau. Rethrow có thể bảo toàn chính loại ngoại lệ ban đầu mà không nới rộng hợp đồng checked exception một cách không cần thiết.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multi-catch-rethrow-006">Precise rethrow và suy ra tập checked exception hẹp hơn</a>

<details>
<summary>Xem chi tiết</summary>

# Precise rethrow và suy ra tập checked exception hẹp hơn trong Java

**Precise rethrow** là khả năng phân tích ngoại lệ của trình biên dịch Java, được bổ sung từ **Java 7**, cho phép một phương thức **bắt ngoại lệ bằng kiểu rộng** (ví dụ `catch (Exception e)`) nhưng khi thực hiện `throw e;`, trình biên dịch có thể xác định **những loại checked exception cụ thể thực sự có thể được ném lại** từ `try` tương ứng. Nhờ đó, chữ ký phương thức không phải luôn khai báo `throws Exception` một cách quá rộng.

Cơ chế này quan trọng khi mã cần ghi nhận một sự cố ở tầng hiện tại nhưng vẫn **ném lại đúng đối tượng exception gốc** cho tầng gọi. Nó đặc biệt hữu ích khi một `try` chứa nhiều thao tác có thể ném các checked exception khác nhau, và chương trình muốn giữ một handler chung mà không đánh mất tính chính xác của hợp đồng `throws`.

Điểm cần ghi nhớ từ đầu: **precise rethrow là phân tích ở thời điểm biên dịch; nó không biến đổi đối tượng ngoại lệ ở runtime, không tự thêm `catch`, không retry thao tác và không chuyển checked exception thành unchecked exception**.

## 1. Vấn đề mà precise rethrow giải quyết

Xét hai thao tác: đọc tệp có thể ném `IOException`, ghi cơ sở dữ liệu có thể ném `SQLException`. Một dịch vụ cần thực hiện đúng một trong hai, ghi nhận lỗi rồi để caller quyết định.

Trước khi xét precise rethrow, người học dễ viết:

```java
static void run(boolean fileMode) throws Exception {
    try {
        if (fileMode) {
            readFile();   // throws IOException
        } else {
            queryDb();    // throws SQLException
        }
    } catch (Exception e) {
        System.out.println("Ghi nhận lỗi");
        throw e;
    }
}
```

Đây là **đoạn minh họa**: `readFile()` và `queryDb()` chưa được định nghĩa, và chữ ký `throws Exception` được viết cố ý rộng. Nếu API thực sự chỉ công bố hai loại checked exception trên, `throws Exception` khiến caller phải xử lý kiểu quá chung. Caller không còn thấy rõ hợp đồng cụ thể từ chữ ký.

Trên Java 7+, khi tham số `catch` thỏa điều kiện *final/effectively final*, Java cho phép viết chính xác hơn:

```java
static void run(boolean fileMode)
        throws IOException, SQLException {
    try {
        if (fileMode) {
            readFile();   // throws IOException
        } else {
            queryDb();    // throws SQLException
        }
    } catch (Exception e) {
        System.out.println("Ghi nhận lỗi");
        throw e;
    }
}
```

Đây cũng là **đoạn minh họa không chạy độc lập**, cần `import java.io.IOException; import java.sql.SQLException;` và hai phương thức tương ứng. Điều đáng chú ý là **biến `e` khai báo là `Exception`**, nhưng compiler vẫn chấp nhận chữ ký `throws IOException, SQLException`, không cần khai báo `throws Exception`.

Tại sao? Bởi compiler phân tích **các loại checked exception có thể phát sinh từ `try`, được handler này bắt và ném lại**. Tham số `e` không bị gán lại, nên tập loại lỗi dùng để kiểm tra lệnh `throw e` có thể hẹp hơn kiểu khai báo `Exception`.

## 2. Ví dụ Java 17 hoàn chỉnh: một `catch`, hai loại checked exception

Lưu chương trình sau thành **`PreciseRethrowDemo.java`**:

```java
import java.io.IOException;
import java.sql.SQLException;

public class PreciseRethrowDemo {
    static void readFile() throws IOException {
        throw new IOException("Không đọc được tệp");
    }

    static void queryDb() throws SQLException {
        throw new SQLException("Không truy cập được DB");
    }

    static void execute(boolean fileMode)
            throws IOException, SQLException {
        try {
            if (fileMode) {
                readFile();
            } else {
                queryDb();
            }
        } catch (Exception e) {
            System.out.println("Service bắt: "
                    + e.getClass().getSimpleName());
            throw e;
        }
    }

    public static void main(String[] args) {
        try {
            execute(true);
        } catch (IOException | SQLException e) {
            System.out.println("Caller: " + e.getMessage());
        }

        try {
            execute(false);
        } catch (IOException | SQLException e) {
            System.out.println("Caller: " + e.getMessage());
        }
    }
}
```

Biên dịch và chạy với Java 17+:

```bash
javac -encoding UTF-8 --release 17 PreciseRethrowDemo.java
java PreciseRethrowDemo
```

**Stdout chính xác** trên console hỗ trợ UTF-8:

```text
Service bắt: IOException
Caller: Không đọc được tệp
Service bắt: SQLException
Caller: Không truy cập được DB
```

Phân tích:

1. `readFile()` công bố checked `IOException`; `queryDb()` công bố checked `SQLException`.
2. `execute` có thể gọi một trong hai. Compiler phân tích cả hai đường đi từ chữ ký và thân `try`.
3. `catch (Exception e)` bắt cả hai loại vì chúng kế thừa `Exception`.
4. `e` không hề bị gán lại. Vì vậy `throw e` được **phân tích chính xác**: hai loại checked exception cần tính đến là `IOException` và `SQLException`.
5. Chữ ký `throws IOException, SQLException` là đủ. `main` vẫn bắt theo kiểu đã khai báo.
6. Khi chạy, `throw e` ném lại **đúng đối tượng** đã được bắt, không tạo instance mới và không thay đổi loại runtime của nó.

`Exception` trong handler là **kiểu tĩnh rộng**; đối tượng thật ở lần thứ nhất là `IOException`, lần thứ hai là `SQLException`. Precise rethrow không biến hai loại đó thành `Exception` mới.

## 3. Quy tắc cốt lõi: tham số `catch` phải là `final` hoặc *effectively final*

Precise rethrow áp dụng khi đối tượng được ném bằng **tham số của mệnh đề `catch`**, với điều kiện tham số ấy được khai báo `final` hoặc **effectively final**.

- `final`: biến được khai báo với từ khóa `final`, nên không được gán lại.
- *Effectively final*: không ghi `final` nhưng chương trình **không gán lại** tham số sau khi nhận giá trị ban đầu.
- `throw e;`: biểu thức ném tham số của handler. Compiler có thể theo dõi nguồn các exception mà handler này bắt từ `try`.

Ví dụ:

```java
catch (Exception e) {
    log(e);
    throw e; // e effectively final, nếu log không gán lại e.
}
```

Đây là **phần thân handler minh họa** với `log(e)` giả định đã định nghĩa. Gọi phương thức và đọc thuộc tính của `e` **không làm mất** tính effectively final. Trái lại, phép gán `e = other;` sẽ làm mất tính chất đó.

Viết `final` tường minh cũng hợp lệ:

```java
catch (final Exception e) {
    throw e;
}
```

Từ khóa `final` ràng buộc **biến tham chiếu**, không khiến toàn bộ đối tượng exception bất biến. Các API cho phép thay đổi dữ liệu trên `Throwable` (ví dụ `addSuppressed` hoặc `setStackTrace`) vẫn có quy tắc riêng. Precise rethrow quan tâm tới **khả năng tham số chuyển sang tham chiếu khác**, không đánh đồng với sự bất biến của object.

## 4. Vì sao việc gán lại `e` phá khả năng suy ra tập hẹp?

Hãy xét phương thức sau. Đây là **phản ví dụ cố ý không biên dịch** trên Java 17:

```java
import java.io.IOException;

class ReassignCatchParameter {
    static void read() throws IOException {
        throw new IOException("Lỗi I/O");
    }

    static void relay() throws IOException {
        try {
            read();
        } catch (Exception e) {
            e = new IOException("Đã thay lỗi"); // Gán lại e.
            throw e; // Compiler phải xét kiểu rộng Exception.
        }
    }
}
```

Vì `e` được khai báo kiểu `Exception` và đã bị **gán lại**, compiler không được áp dụng quy tắc precise rethrow cho tham số này như trước. `throw e` có thể ném một `Exception` theo kiểu tĩnh rộng, nên chữ ký chỉ `throws IOException` không đủ. Lỗi thường thể hiện dưới dạng **unreported exception java.lang.Exception; must be caught or declared to be thrown**.

Điều này không có nghĩa mọi phép gán lại `e` đều bị Java cấm. Với **single-catch parameter** không có `final`, việc gán lại có thể hợp lệ về cú pháp, nhưng làm thay đổi đối tượng đang được tham chiếu và khả năng phân tích checked exception.

Các lựa chọn đúng tùy mục tiêu:

- Nếu cần **giữ cùng đối tượng**, không gán lại `e` và dùng `throw e;`.
- Nếu muốn **tạo ngoại lệ mới** với hợp đồng khác, hãy tạo biến/đối tượng mới, giữ lỗi gốc qua `cause` nếu thích hợp.
- Nếu thực sự cho phép ném một `Exception` bất kỳ, khai báo `throws Exception` là hợp lệ nhưng cần cân nhắc độ rộng của API.

Không nên đổi `e` thành một instance mới rồi gọi đó là “rethrow nguyên bản”: caller sẽ nhận object mới chứ không phải exception đã phát sinh ở thao tác gốc.

## 5. Định nghĩa chính xác: suy ra từ `try` nào?

Khi một `catch` bắt nhiều loại có thể ném ra từ `try`, compiler không chỉ nhìn **tên loại của tham số `catch`**. Nó xét những exception có thể được phát sinh bởi **khối `try` tương ứng**, và những loại mà handler hiện tại có thể bắt, sau khi tính đến các handler đứng trước.

Có thể hình dung quá trình phân tích cho một `throw e` trong handler final/effectively final:

1. Xác định **`try` gắn với handler chứa `e`**.
2. Xác định các loại exception có thể phát sinh theo phân tích tĩnh từ `try`, bao gồm các lời gọi phương thức và những cấu trúc hợp lệ bên trong.
3. Xác định trong số đó loại nào **được handler hiện tại bắt**, không bị một handler trước trong cùng chuỗi `catch` bắt mất.
4. Dùng các loại tương ứng trong việc kiểm tra những **checked exception** của `throw e`.
5. Những loại là **unchecked** không yêu cầu `throws` trong chữ ký theo quy tắc catch-or-declare.

Đây là **mô hình suy luận thực hành**, không phải trình tự từng bước của bộ máy JVM. Ngôn ngữ Java quy định các điều kiện kiểm tra tại **compile time**, còn JVM vẫn nhận một đối tượng exception đơn lẻ khi chạy.

Không nên hiểu rằng compiler “chạy thử” nhánh `if` với dữ liệu thật để biết chính xác một exception runtime cụ thể. Nó phân tích **khả năng** từ kiểu và cấu trúc, không biết mọi giá trị đầu vào trong tương lai.

## 6. Handler trước đã bắt một loại: tập loại còn lại có thể hẹp hơn

Precise rethrow tính đến việc những exception cụ thể đã được `catch` **đứng trước** xử lý.

Ví dụ Java 17 đầy đủ, lưu **`EarlierCatchDemo.java`**:

```java
import java.io.IOException;
import java.sql.SQLException;

public class EarlierCatchDemo {
    static void execute(boolean ioFailure)
            throws IOException, SQLException {
        if (ioFailure) {
            throw new IOException("Tệp lỗi");
        }
        throw new SQLException("DB lỗi");
    }

    static void relay(boolean ioFailure) throws SQLException {
        try {
            execute(ioFailure);
        } catch (IOException e) {
            System.out.println("I/O đã xử lý: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Lỗi còn lại: "
                    + e.getClass().getSimpleName());
            throw e;
        }
    }

    public static void main(String[] args) {
        relaySafely(true);
        relaySafely(false);
    }

    static void relaySafely(boolean ioFailure) {
        try {
            relay(ioFailure);
        } catch (SQLException e) {
            System.out.println("Caller: " + e.getMessage());
        }
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 --release 17 EarlierCatchDemo.java
java EarlierCatchDemo
```

**Stdout chính xác**:

```text
I/O đã xử lý: Tệp lỗi
Lỗi còn lại: SQLException
Caller: DB lỗi
```

Giải thích:

- `execute` có thể phát sinh `IOException` hoặc `SQLException`.
- `catch (IOException e)` đầu tiên xử lý đường I/O và **không ném tiếp**.
- `catch (Exception e)` phía sau không còn nhận `IOException` từ `try` đó, do loại này đã được handler trước bắt.
- Vì `e` trong handler sau là effectively final, `throw e` được kiểm tra chính xác; với các checked exception của `try` trong ví dụ, chỉ còn **`SQLException`**.
- Do đó `relay` chỉ cần khai báo `throws SQLException`, không cần `throws Exception` hay `throws IOException`.

Đừng nhầm với tình huống handler `catch (IOException e)` **ném một ngoại lệ mới**: exception phát sinh từ thân `catch` trước không được thử bắt bởi handler `catch (Exception e)` tiếp theo ở cùng cấu trúc. Nó sẽ đi ra phạm vi bao ngoài hoặc caller theo quy tắc lan truyền.

## 7. Multi-catch và precise rethrow liên quan như thế nào?

Java 7 cũng giới thiệu **multi-catch**: một handler có thể nêu nhiều kiểu exception bằng dấu `|`, ví dụ:

```java
catch (IOException | SQLException e) {
    throw e;
}
```

Với multi-catch:

- Tham số `e` của multi-catch là **implicitly final**: không thể gán lại.
- Handler chỉ nhận **một đối tượng exception** tại mỗi lần thực thi, thuộc một trong các loại đã liệt kê.
- Khi `throw e`, đối tượng thật được ném lại; không tạo object mới.
- Compiler có thể giữ các loại checked exception riêng biệt trong phân tích, thay vì buộc công bố một kiểu cha quá rộng.
- Các kiểu liệt kê trong cùng multi-catch **không được có quan hệ subtype với nhau**, ví dụ `IOException | Exception` không hợp lệ.

Ví dụ hoàn chỉnh, lưu **`MultiCatchPreciseDemo.java`**:

```java
import java.io.IOException;
import java.sql.SQLException;

public class MultiCatchPreciseDemo {
    static void task(boolean io) throws IOException, SQLException {
        if (io) {
            throw new IOException("Lỗi tệp");
        }
        throw new SQLException("Lỗi DB");
    }

    static void relay(boolean io) throws IOException, SQLException {
        try {
            task(io);
        } catch (IOException | SQLException e) {
            System.out.println("Relay: " + e.getClass().getSimpleName());
            throw e;
        }
    }

    public static void main(String[] args) {
        for (boolean io : new boolean[] {true, false}) {
            try {
                relay(io);
            } catch (IOException | SQLException e) {
                System.out.println("Caller: " + e.getMessage());
            }
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 MultiCatchPreciseDemo.java
java MultiCatchPreciseDemo
```

**Stdout chính xác**:

```text
Relay: IOException
Caller: Lỗi tệp
Relay: SQLException
Caller: Lỗi DB
```

Trong multi-catch, không thể viết `e = new IOException("x");` vì tham số ngầm định `final`. Hai cơ chế **multi-catch** và **precise rethrow** phối hợp tốt, nhưng là hai khái niệm khác nhau:

- Multi-catch cho phép **gộp cú pháp xử lý** nhiều loại exception không có quan hệ subtype.
- Precise rethrow cho phép **kiểm tra chính xác checked exception ở lệnh ném lại** khi điều kiện trên tham số được đáp ứng.

Không phải cứ dùng multi-catch mới có precise rethrow: ví dụ `catch (Exception e)` với `e` effectively final ở phần trước đã chứng minh điều ngược lại.

## 8. `catch (Exception e)` vẫn bắt những loại khác ngoài checked exception

`Exception` là lớp cha của `RuntimeException`. Vì vậy một `catch (Exception e)` cũng có thể bắt các unchecked exception như `IllegalArgumentException` và `NullPointerException` phát sinh trong `try`. Tuy nhiên, điều đó **không bắt buộc** phương thức khai báo `throws RuntimeException` hoặc `throws NullPointerException`.

Ví dụ:

```java
static void run(String text) throws IOException {
    try {
        readFile();                   // throws IOException
        Integer.parseInt(text);       // Có thể ném NumberFormatException
    } catch (Exception e) {
        throw e;
    }
}
```

Đây là **phương thức minh họa**, cần `readFile()` được định nghĩa và `IOException` được import. Chữ ký `throws IOException` đủ về mặt checked exception vì `NumberFormatException` là một lớp con unchecked của `RuntimeException`. Khi chạy, handler có thể nhận một trong hai và ném lại đúng object; caller phải quyết định có bắt những unchecked exception đó hay không theo hợp đồng.

**`catch (Exception e)` không bắt các lớp `Error`**, vì `Error` là nhánh anh em của `Exception` dưới `Throwable`. Không nên thay thành `catch (Throwable e)` chỉ để “bắt tất cả” trong mã nghiệp vụ: nhiều `Error` nghiêm trọng và không phù hợp để tiếp tục bình thường.

Một đoạn Java biên dịch được **không bảo đảm** mọi nhánh lỗi đã được xử lý an toàn. Precise rethrow chỉ giải quyết mức chính xác của hợp đồng checked exception khi compiler kiểm tra.

## 9. Kiểu tĩnh của phương thức được gọi đặt giới hạn cho sự chính xác

Compiler phân tích **chữ ký và cấu trúc mã nhìn thấy được**, không tự suy ra kiểu ngoại lệ từ những gì bạn *biết* về cách triển khai ở runtime.

Giả sử API công bố:

```java
static void externalApi() throws Exception {
    throw new IOException("Lỗi I/O");
}
```

Đây là **phương thức minh họa**, cần import `IOException`. Mặc dù thân minh họa chỉ ném `IOException`, **caller** thấy chữ ký `throws Exception`; compiler phải tính đến khả năng checked exception thuộc kiểu `Exception` từ hợp đồng đã công bố.

Bởi vậy đoạn **cố ý không biên dịch** sau không thể dựa vào precise rethrow để chỉ khai báo `IOException`:

```java
static void caller() throws IOException {
    try {
        externalApi();  // Declared throws Exception
    } catch (Exception e) {
        throw e;        // Có thể phải khai báo throws Exception
    }
}
```

Bởi `externalApi()` được khai báo `throws Exception`, tại vị trí gọi, loại checked exception có thể phát sinh theo phân tích là `Exception`, không chỉ `IOException`. Dù tham số `e` effectively final, nó không thể làm chữ ký caller chính xác hơn thông tin có được từ API được gọi.

Sửa theo **hợp đồng thật sự**:

- Nếu sở hữu `externalApi` và chắc chắn hợp đồng chỉ có lỗi I/O, thay chữ ký của nó thành `throws IOException` để caller có thông tin chính xác.
- Nếu API chủ đích cho phép mọi `Exception` checked, caller có thể khai báo `throws Exception` hoặc bắt/chuyển các trường hợp tương ứng.
- Nếu đổi tầng trừu tượng bằng wrapper mới, hãy chủ động thiết kế hợp đồng và giữ cause; không coi đó là precise rethrow của đối tượng gốc.

Đừng “ép kiểu” exception để qua compiler khi không có bảo đảm về loại runtime thực tế. Một `ClassCastException` trong xử lý lỗi chỉ khiến tình huống tệ hơn.

## 10. Checked exception của tài nguyên cũng nằm trong phân tích `try`

Một khối `try-with-resources` có thể phát sinh checked exception **khi đóng resource**, dù thân `try` không ném checked exception nào. Vì vậy precise rethrow không chỉ nhìn các lệnh nghiệp vụ ở giữa dấu ngoặc nhọn: phải tính cả những thao tác đóng do cấu trúc ngôn ngữ thực hiện.

Ví dụ Java 17 độc lập **`ResourcePreciseDemo.java`**:

```java
import java.io.IOException;

public class ResourcePreciseDemo {
    static class Resource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            System.out.println("Đang đóng");
            throw new IOException("Không đóng được");
        }
    }

    static void use() throws IOException {
        try (Resource resource = new Resource()) {
            System.out.println("Trong try");
        } catch (Exception e) {
            System.out.println("Catch: "
                    + e.getClass().getSimpleName());
            throw e;
        }
    }

    public static void main(String[] args) {
        try {
            use();
        } catch (IOException e) {
            System.out.println("Caller: " + e.getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 ResourcePreciseDemo.java
java ResourcePreciseDemo
```

**Stdout chính xác**:

```text
Trong try
Đang đóng
Catch: IOException
Caller: Không đóng được
```

`close()` khai báo `throws IOException`. Dù thân `try` chỉ in ra console, kết thúc phạm vi sẽ gọi `close()`, nơi ném `IOException`. Handler rộng `catch (Exception e)` nhận lỗi, rồi `throw e`; compiler cho phép `use() throws IOException` vì phân tích checked exception bao gồm lỗi có thể phát sinh khi đóng tài nguyên.

Nếu **cả thân `try` và `close()` cùng ném exception**, `try-with-resources` thường giữ lỗi của thân làm exception chính và gắn lỗi đóng làm **suppressed exception**. `throw e` giữ nguyên đối tượng chính và danh sách suppressed hiện có; nó không tự xóa lỗi đóng. Đừng dùng `finally` có `throw` mới một cách vô ý để che mất lỗi gốc.

## 11. Precise rethrow và ném lại đúng đối tượng

`catch (Exception e) { throw e; }` **không tự gọi `new Exception(...)`**. Ở runtime, `throw e` dùng đúng reference tới đối tượng đã bắt. Precise rethrow là việc *compiler tính nghĩa vụ checked exception*, không phải tạo object mới hoặc gỡ bỏ stack trace.

Ví dụ nhận diện identity trong **`IdentityPreciseDemo.java`**:

```java
import java.io.IOException;

public class IdentityPreciseDemo {
    static IOException original;

    static void relay() throws IOException {
        original = new IOException("Lỗi gốc");
        try {
            throw original;
        } catch (Exception e) {
            System.out.println("Trong catch: " + (e == original));
            throw e;
        }
    }

    public static void main(String[] args) {
        try {
            relay();
        } catch (IOException e) {
            System.out.println("Tại caller: " + (e == original));
            System.out.println("Message: " + e.getMessage());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 IdentityPreciseDemo.java
java IdentityPreciseDemo
```

**Stdout chính xác**:

```text
Trong catch: true
Tại caller: true
Message: Lỗi gốc
```

Mục đích của field `original` chỉ để kiểm tra identity, không phải mẫu chia sẻ exception qua biến `static` cho chương trình đa luồng. Ở mã thực, hãy để cơ chế ném và bắt truyền thông tin lỗi.

`throw e` thông thường cũng không tự tạo stack trace mới chỉ vì ném lại. Nếu cần đổi loại lỗi do tầng trừu tượng, dùng wrapper với `cause` (ví dụ `new IllegalStateException("Không nạp được", e)`); đó là **đối tượng mới**, không còn cùng identity, và khác precise rethrow nguyên bản.

## 12. Kết hợp với các handler lồng nhau

Một `try` bên trong có thể bắt và giải quyết `IOException`; `try` bên ngoài chỉ còn thấy những exception được truyền lên thực tế theo phân tích luồng.

Ví dụ khung:

```java
static void dispatch() throws SQLException {
    try {
        try {
            readFile();  // throws IOException
        } catch (IOException e) {
            System.out.println("Đã xử lý I/O");
        }

        queryDb();       // throws SQLException
    } catch (Exception e) {
        throw e;
    }
}
```

Đây là **đoạn minh họa**, cần hai phương thức `readFile` và `queryDb` cùng import các loại exception. Nếu handler I/O bên trong kết thúc bình thường và không ném lại, outer `try` không để `IOException` ấy thoát ra. `throw e` ở handler ngoài có thể được kiểm tra với checked exception còn lại là `SQLException`.

Ngược lại, nếu handler trong `catch (IOException e)` viết `throw e;` thì `IOException` có thể lan ra phạm vi ngoài. Khi đó cần khai báo hoặc xử lý nó; precise rethrow không tự “xóa” một exception có thể thực sự thoát ra.

Lưu ý: việc đặt các `catch` riêng biệt còn giúp mã dễ đọc. Đừng lồng nhiều tầng chỉ để đạt chữ ký hẹp hơn nếu một tổ chức handler đơn giản đã thể hiện rõ trách nhiệm xử lý.

## 13. Trường hợp không thuộc precise rethrow

Dễ nhầm các trường hợp sau là cùng một cơ chế:

### 13.1. Ném một biến ngoại lệ không phải tham số `catch`

```java
static void rethrowVariable(Exception saved) throws Exception {
    throw saved;
}
```

Đây là **phương thức hợp lệ không có `main`**. `saved` là tham số phương thức, **không phải tham số `catch` gắn với `try`**. Dù bạn không gán lại `saved`, compiler không dùng quy tắc precise rethrow từ một `try` như với catch parameter. Vì kiểu tĩnh của `saved` là `Exception`, chữ ký `throws Exception` là phù hợp.

### 13.2. Tạo đối tượng exception mới

```java
catch (Exception e) {
    throw new Exception("Lỗi mới", e);
}
```

Đây là **đoạn handler minh họa**. Lệnh ném biểu thức `new Exception(...)`, không phải `throw e`. Exception mới có loại checked `Exception`; phương thức phải bắt/khai báo nó theo hợp đồng. Cơ chế precise rethrow dành cho tham số catch không làm loại exception mới trở thành `IOException` chỉ vì `cause` của nó là `IOException`.

### 13.3. Chỉ có một `catch` cụ thể

```java
catch (IOException e) {
    throw e;
}
```

Handler này ném lại kiểu khai báo `IOException`; ngay cả không bàn về precise rethrow, chữ ký `throws IOException` đã hiển nhiên đúng. Hiệu quả nổi bật nhất của precise rethrow là tình huống **bắt kiểu rộng** nhưng không buộc công bố tất cả checked exception thuộc kiểu rộng.

### 13.4. Bọc checked exception thành unchecked

```java
catch (IOException e) {
    throw new IllegalStateException("Tầng dịch vụ lỗi", e);
}
```

Đoạn này tạo **unchecked exception** mới, giữ `IOException` qua `getCause()`; không yêu cầu khai báo checked `IOException` từ việc ném wrapper. Nhưng đây là **đổi hợp đồng**, không phải precise rethrow. Lựa chọn này chỉ nên dùng khi hợp đồng tầng mới yêu cầu một ngoại lệ ứng dụng phù hợp.

## 14. Precise rethrow không xóa trách nhiệm xử lý lỗi

Một chữ ký gọn không có nghĩa mọi thất bại đều được phục hồi. Khi rethrow:

- Phương thức hiện tại vẫn **kết thúc bất thường** nếu exception thoát ra.
- Caller vẫn cần biết nên thử phương án dự phòng, trả lỗi cho người dùng, dừng yêu cầu hay chuyển tiếp.
- `finally` và `try-with-resources` vẫn tham gia cleanup theo quy tắc; không tự rollback dữ liệu hoặc hoàn tác mọi side effect.
- Các unchecked exception có thể đi lên dù không xuất hiện trong `throws`.
- Kiểu tĩnh chính xác hơn giúp **caller đọc API và dùng `catch` cụ thể**; không bảo đảm hệ thống không có lỗi runtime.
- Nếu không có việc gì cần làm ở tầng trung gian, có thể **bỏ `catch` chỉ để ném lại**, để exception lan tự nhiên.

Một nơi phù hợp để dùng handler bắt rộng rồi rethrow là tầng cần bổ sung ngữ cảnh **mà không được làm thay đổi hợp đồng checked exception**. Nhưng hãy xem liệu bắt cụ thể hoặc multi-catch có rõ ràng hơn; đừng dùng `catch (Exception e)` vô điều kiện, vì nó còn bắt nhiều `RuntimeException` có thể là bug.

## 15. Lỗi thường gặp

| Hiểu sai | Cách hiểu đúng |
| --- | --- |
| “`catch (Exception e)` rồi `throw e` luôn phải `throws Exception`.” | Java 7+ có precise rethrow khi `e` final/effectively final; có thể khai báo các checked exception hẹp hơn. |
| “Precise rethrow chuyển checked thành unchecked.” | Không; nó chỉ phân tích tập checked exception có thể ném lại chính xác hơn. |
| “Chỉ multi-catch mới có precise rethrow.” | Không; single-catch rộng `catch (Exception e)` cũng có thể được phân tích chính xác. |
| “`catch (Exception e)` chỉ bắt checked exception.” | Nó còn bắt `RuntimeException`, nhưng không bắt `Error`. |
| “`throw e` tạo đối tượng exception mới.” | Nó ném lại cùng object mà `e` tham chiếu, nếu `e` chưa bị gán lại. |
| “Một handler đứng sau vẫn nhận loại đã bị handler trước bắt.” | Không; các handler được xét theo thứ tự, loại đã bắt ở trước không được chuyển sang handler sau. |
| “Compiler biết chắc lúc chạy chỉ ném `IOException` dù callee khai báo `throws Exception`.” | Compiler kiểm tra hợp đồng tĩnh; kiểu `Exception` rộng vẫn phải được xét. |
| “Viết `throws IOException` là đủ chỉ vì exception ban đầu có cause là IOException.” | Một wrapper checked mới có thể là `Exception`; cause không thay đổi kiểu checked của wrapper. |
| “Gán lại `e` vẫn giữ precise rethrow như cũ.” | Gán lại tham số single-catch làm mất điều kiện effectively final; multi-catch còn cấm gán lại. |
| “Precise rethrow tự xử lý cleanup và retry.” | Không; đó là những vấn đề riêng về luồng thực thi, tài nguyên và chính sách. |

## 16. Tự kiểm tra với các tình huống

**Câu 1.** Thân `try` có thể ném `IOException` và `SQLException`; handler `catch (Exception e) { throw e; }` không gán lại `e`. Có cần `throws Exception` không?  
**Đáp án:** Không nhất thiết. `throws IOException, SQLException` có thể đủ theo precise rethrow, nếu đó là các checked exception có thể thoát ra và không có loại checked khác phát sinh ở handler.

**Câu 2.** Trong `catch (Exception e)`, thêm `e = new IOException("khác");` trước `throw e;` có còn precise rethrow không?  
**Đáp án:** Không theo cơ chế dành cho catch parameter final/effectively final, vì `e` đã bị gán lại. Nếu chữ ký chỉ `throws IOException`, compiler có thể yêu cầu kiểu rộng hơn `Exception`.

**Câu 3.** `catch (IOException | SQLException e)` có thể gán lại `e` không?  
**Đáp án:** Không. Tham số multi-catch implicitly final.

**Câu 4.** Thân `try` có `IOException` và `SQLException`, nhưng handler đầu bắt `IOException` và xử lý xong. Handler sau là `catch (Exception e) { throw e; }`. Tập checked exception cần xét cho handler sau là gì?  
**Đáp án:** Theo ví dụ thông thường, còn `SQLException`; `IOException` đã được handler trước bắt, miễn là handler trước không ném thêm lỗi checked cần tính riêng.

**Câu 5.** Vì sao precise rethrow không thể thu hẹp khi gọi một API công bố `throws Exception` nhưng bạn biết phiên bản hiện tại chỉ ném `IOException`?  
**Đáp án:** Compiler phải xét hợp đồng tĩnh của lời gọi, trong đó `Exception` vẫn là kiểu checked có thể phát sinh.

**Câu 6.** `throw e` có sửa message, cause hoặc stack trace của exception gốc không?  
**Đáp án:** Bản thân lệnh không tạo object mới hay tự thay đổi các thuộc tính đó. Dữ liệu có thể bị thay đổi bởi code khác hoặc cơ chế đặc biệt của JVM, nhưng không vì hành động rethrow đơn thuần.

**Câu 7.** Nếu resource `close()` khai báo `throws IOException` nhưng thân `try` không ném checked exception, `catch (Exception e) { throw e; }` có thể khiến phương thức cần `throws IOException` không?  
**Đáp án:** Có. Phân tích ngoại lệ của `try-with-resources` tính cả lỗi có thể ném khi đóng tài nguyên.

**Câu 8.** Một handler `catch (Exception e) { throw new IOException("mới"); }` có phải precise rethrow không?  
**Đáp án:** Không. Nó ném một đối tượng mới có kiểu checked `IOException`; luật kiểm tra loại ném mới áp dụng, không phải phân tích `throw e` của tham số catch.

**Câu 9.** Một phương thức `throws IOException, SQLException` có thể còn ném `NullPointerException` không?  
**Đáp án:** Có. Đây là unchecked exception, không bắt buộc khai báo.

**Câu 10.** Vì sao không nên viết `catch (Exception e)` ở khắp mọi nơi chỉ để tận dụng precise rethrow?  
**Đáp án:** Vì phạm vi bắt quá rộng có thể vô tình bao gồm lỗi lập trình; chỉ nên bắt khi tầng hiện tại thật sự cần làm việc có ý nghĩa trước khi chuyển lỗi đi.

## 17. Checklist kiểm tra một đoạn precise rethrow

Khi review một phương thức chứa `throw e`, hãy lần lượt kiểm tra:

1. **`e` có phải tham số của `catch` không?** Một tham số phương thức thông thường không hưởng quy tắc này theo cách tương tự.
2. **`e` có final hoặc effectively final không?** Không được có phép gán lại.
3. **`try` tương ứng có thể ném những checked exception nào?** Xem cả chữ ký phương thức được gọi, cấu trúc bên trong và `close()` của tài nguyên.
4. **Có handler nào đứng trước đã bắt một số loại?** Chỉ các exception còn tới được handler hiện tại mới liên quan với precise rethrow của handler ấy.
5. **Có exception mới ném từ handler không?** Cần tính riêng, không coi nó là rethrow nguyên bản.
6. **Chữ ký `throws` có mô tả đúng tập checked exception có thể thoát ra?** Không ghi quá hẹp trái quy tắc, cũng hạn chế kiểu rộng không cần thiết.
7. **Có unchecked exception không?** Không phải khai báo nhưng vẫn phải quản lý rủi ro theo hợp đồng.
8. **Có thực sự cần `catch` không?** Nếu handler chỉ `throw e` mà không bổ sung giá trị, để exception tự lan truyền có thể đơn giản hơn.
9. **Caller có hành động xử lý cuối phù hợp không?** Chữ ký chính xác giúp caller xử lý, không thay thế chính sách phục hồi.
10. **Ví dụ đã chạy trên phiên bản Java dùng trong dự án chưa?** Precise rethrow có từ Java 7; các API và cú pháp khác trong ví dụ có thể yêu cầu phiên bản mới hơn.

## 18. Kết luận

**Precise rethrow** giúp Java suy ra **tập checked exception hẹp hơn** khi một handler bắt kiểu rộng nhưng ném lại **chính tham số catch final/effectively final** bằng `throw e;`. Compiler dựa vào các checked exception có thể phát sinh từ `try` tương ứng, có tính tới những handler đứng trước và cấu trúc tài nguyên, thay vì đơn giản bắt chữ ký phải `throws Exception` chỉ vì `e` có kiểu khai báo `Exception`.

Multi-catch phối hợp tốt với precise rethrow vì tham số của nó ngầm định `final`, nhưng không phải điều kiện bắt buộc để precise rethrow hoạt động. Không gán lại `e`, không nhầm `throw e` với `throw new ...`, và không mong compiler suy luận hẹp hơn chữ ký tĩnh của API được gọi.

Quy tắc thực hành là: **để tầng hiện tại xử lý khi có khả năng phục hồi; nếu cần ghi nhận rồi lan truyền, giữ nguyên ngoại lệ và khai báo tập checked exception chính xác nhất mà ngôn ngữ cho phép**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multi-catch-rethrow-007">Ảnh hưởng của final, effectively final và việc gán lại tham số catch</a>

<details>
<summary>Xem chi tiết</summary>

# Ảnh hưởng của `final`, *effectively final* và việc gán lại tham số `catch` trong Java

Trong Java, biến tham số của `catch` không chỉ dùng để đọc thông điệp ngoại lệ. **Có được gán lại tham số đó hay không** còn tác động đến khả năng sử dụng trong lambda/lớp ẩn danh và, đặc biệt, cách trình biên dịch phân tích kiểu checked exception khi viết `throw ex;` (*precise rethrow*).

Ba quy tắc cốt lõi:

1. Tham số của **single-catch** (ví dụ `catch (IOException ex)`) thông thường **có thể gán lại** nếu không khai báo `final`. Nếu mã thực tế không gán lại, tham số có thể là **effectively final**.
2. Tham số của **multi-catch** (ví dụ `catch (IOException | SQLException ex)`) **ngầm là `final`**. Không thể gán lại nó, ngay cả khi không viết từ khóa `final`.
3. Một tham số `catch` đơn với kiểu rộng như `Exception` có thể được trình biên dịch phân tích **ném lại chính xác hơn kiểu khai báo** nếu nó `final` hoặc *effectively final*. Gán lại tham số có thể làm mất tính chính xác đó và dẫn đến lỗi biên dịch checked exception.

Vì vậy, phải phân biệt **kiểu tĩnh của tham số `catch`**, **kiểu runtime của đối tượng ngoại lệ**, **khả năng gán lại biến**, và **hợp đồng `throws` của phương thức**. Chúng liên quan nhưng không đồng nhất.

## 1. Nhắc lại: tham số `catch` là gì?

Một mệnh đề `catch` khai báo một tham số đại diện cho đối tượng ngoại lệ được handler tiếp nhận:

```java
try {
    Integer.parseInt("abc");
} catch (NumberFormatException ex) {
    System.out.println(ex.getClass().getSimpleName());
}
```

Đầu ra:

```text
NumberFormatException
```

- `NumberFormatException` là **kiểu khai báo** của tham số.
- `ex` là **biến tham chiếu** tới đối tượng ngoại lệ đang được xử lý.
- `ex.getMessage()`, `ex.getCause()`, `ex.getStackTrace()` cho phép xem thông tin chẩn đoán, tùy tình huống.
- Phạm vi của `ex` là **thân handler `catch`** tương ứng; biến này không tồn tại ở các nhánh `catch` anh em hay sau cấu trúc `try-catch`.

Thông thường, bạn **không cần gán lại `ex`**. Người viết code sẽ đọc ngoại lệ, xử lý hoặc ném lại. Nhưng Java cho phép gán lại một số biến `catch`, và ở đó phát sinh khác biệt giữa single-catch và multi-catch.

## 2. `final` thực sự có nghĩa gì với tham số `catch`?

`final` trên một biến tham chiếu cấm thay đổi **giá trị tham chiếu của biến đó** sau khi biến được khởi tạo. Nó **không làm đối tượng được tham chiếu trở thành bất biến**.

Ví dụ minh họa trong một phương thức:

```java
try {
    throw new IllegalArgumentException("bad input");
} catch (final IllegalArgumentException ex) {
    System.out.println(ex.getMessage());
    // ex = new IllegalArgumentException("other"); // Lỗi biên dịch.
}
```

Đây là code minh họa; dòng bị comment nếu bỏ comment sẽ cố ý không biên dịch, do `ex` được khai báo `final`.

Điều **không** được phép với biến `final`:

```java
// Sai nếu ex đã được khai báo final:
ex = new IllegalArgumentException("replacement");
ex = null;
```

Điều **có thể** làm:

```java
System.out.println(ex.getMessage());
Throwable cause = ex.getCause();
```

`final` không làm biến mất khả năng gọi phương thức trên đối tượng. Các phương thức của đối tượng có thể thay đổi trạng thái nếu API cho phép; `final` chỉ kiểm soát việc gán lại **biến tham chiếu**.

**Phân biệt:** `final` không ngăn `throw ex;`. Lệnh `throw` chỉ sử dụng tham chiếu để ném đối tượng, không phải phép gán vào biến `ex`.

## 3. *Effectively final* là gì?

Một biến hoặc tham số được gọi là **effectively final** khi mã tuân thủ các quy tắc không gán lại phù hợp của ngôn ngữ, dù **không viết rõ từ khóa `final`**. Với tham số `catch` đơn, hiểu thực hành là: sau khi tham số nhận đối tượng ngoại lệ, mã không gán lại giá trị cho tham số đó.

Ví dụ:

```java
try {
    Integer.parseInt("abc");
} catch (NumberFormatException ex) {
    System.out.println(ex.getMessage());
    // Không có phép gán ex = ...
}
```

`ex` không khai báo `final`, nhưng vẫn **effectively final**.

Ngược lại:

```java
try {
    Integer.parseInt("abc");
} catch (NumberFormatException ex) {
    ex = new NumberFormatException("custom description");
    System.out.println(ex.getMessage());
}
```

Đoạn mã thứ hai **có thể biên dịch** vì đây là **single-catch** không khai báo `final`. Nhưng tham số `ex` không còn *effectively final*.

Cần chú ý:

- `ex = ex;` vẫn là **một phép gán**, nên không được coi là cách giữ *effectively final*.
- Một phép gán trong nhánh `if` có điều kiện cũng có thể làm mất thuộc tính này; không phải cứ nhánh đó ít chạy là trình biên dịch bỏ qua.
- Gọi `ex.getMessage()` không phải phép gán vào `ex`.
- Gán một biến **khác**, ví dụ `Exception original = ex;`, không làm thay đổi tính *effectively final* của `ex`.
- Thay đổi tham chiếu `ex` không thay đổi đối tượng ngoại lệ ban đầu; nó chỉ khiến tên biến `ex` trỏ tới đối tượng mới.

Một điểm thú vị: *effectively final* không phải kiểu dữ liệu mới, không xuất hiện ở runtime và không khiến JVM “khóa” đối tượng. Đây là khái niệm để **trình biên dịch** kiểm tra và phân tích chương trình.

## 4. Single-catch: được phép gán lại, nhưng nên hiểu hệ quả

`catch (IOException ex)` là **single-catch** với một kiểu ngoại lệ:

```java
try {
    readData();
} catch (java.io.IOException ex) {
    java.io.IOException original = ex;
    ex = new java.io.IOException("higher-level context", original);
    System.out.println(ex.getMessage());
}
```

Đây là **đoạn minh họa**, giả sử `readData()` có thể ném `IOException`. Nếu không khai báo `final`, phép gán `ex = ...` hợp lệ.

Tuy nhiên, có hai đối tượng cần phân biệt:

- `original` tiếp tục tham chiếu tới ngoại lệ được bắt ban đầu.
- `ex` sau phép gán trỏ tới ngoại lệ mới. Nếu code chỉ kết thúc handler bình thường thì ngoại lệ mới **không tự được ném** lên caller.

Do đó, **gán lại `ex` không phải là một cách để chuyển ngoại lệ**. Nếu cần bọc lỗi và gửi ra ngoài, hãy ném đối tượng mới:

```java
try {
    readData();
} catch (java.io.IOException cause) {
    throw new java.io.UncheckedIOException(
            "Cannot load required data", cause);
}
```

Đây là đoạn minh họa, cần có `readData()` thực sự; `UncheckedIOException` là lớp `RuntimeException` chuyên dùng để bọc `IOException`. Constructor giữ `cause` để truy vết.

**Thực hành tốt:** sử dụng một biến mới như `cause` hoặc `wrapped` khi cần biến đổi ngữ cảnh; không gán lại chính tham số `catch` chỉ để đổi thông điệp. Việc gán lại khiến mã khó đọc và, với một `catch` kiểu rộng, còn có thể ảnh hưởng đến phân tích *precise rethrow*.

## 5. Multi-catch: tham số ngầm `final`

Java hỗ trợ bắt nhiều loại lỗi khác nhánh bằng một handler:

```java
try {
    perform();
} catch (java.io.IOException | java.sql.SQLException ex) {
    System.out.println(ex.getClass().getSimpleName());
}
```

Đây là cấu trúc minh họa. Để biên dịch nguyên dạng, `perform()` phải có hợp đồng phù hợp, chẳng hạn `throws IOException, SQLException`.

Trong multi-catch:

- Các loại trong danh sách phân cách bằng `|` không được có quan hệ **lớp cha – lớp con** với nhau.
- Các lựa chọn đều cùng dùng một biến `ex`.
- Biến `ex` **implicitly final**, nghĩa là ngầm được xem như `final` dù không có từ khóa này.
- Không thể gán lại `ex` bằng một ngoại lệ mới hay bằng `null`.
- Có thể đọc `ex`, xem kiểu thực tế, in thông điệp, chuyển nó làm `cause` hoặc `throw ex;` nếu hợp đồng checked exception cho phép.
- Kiểu tĩnh dùng để truy cập thành viên của `ex` được suy ra từ các lựa chọn của multi-catch; không thể mặc định gọi những phương thức chỉ có ở một nhánh nếu không kiểm tra kiểu phù hợp.

Ví dụ **cố ý không biên dịch**:

```java
try {
    perform();
} catch (java.io.IOException | java.sql.SQLException ex) {
    ex = new java.io.IOException("replacement"); // Lỗi: ex ngầm final.
}
```

Tương tự, dòng `ex = ex;` vẫn không được phép. Nếu cần một tham chiếu mới để thao tác, hãy khai báo **biến khác** trong handler; biến `ex` tiếp tục giữ đối tượng đã được bắt.

Một lỗi cú pháp/kiểu khác:

```java
// Sai: NumberFormatException là lớp con của IllegalArgumentException.
catch (NumberFormatException | IllegalArgumentException ex) {
    // ...
}
```

Đoạn trên **cố ý không biên dịch** vì lựa chọn thứ hai đã bao phủ lựa chọn thứ nhất. Sử dụng `catch (IllegalArgumentException ex)` hoặc tách handler cụ thể nếu hành động xử lý khác nhau.

## 6. Bảng so sánh chính xác

| Khía cạnh | Single-catch `catch (IOException ex)` | Single-catch `catch (final IOException ex)` | Multi-catch `catch (IOException | SQLException ex)` |
| --- | --- | --- | --- |
| Có từ khóa `final`? | Không | Có | Không cần |
| Biến có thể gán lại? | **Có**, trừ quy tắc khác áp dụng | **Không** | **Không**, vì ngầm `final` |
| Có thể effectively final? | **Có**, nếu không gán lại | Đã `final` tường minh | Đã `final` ngầm |
| Lambda có thể bắt giữ `ex`? | Có nếu effectively final | Có | Có |
| `throw ex;` có hợp lệ? | Có nếu thỏa quy tắc checked exception | Có nếu thỏa quy tắc checked exception | Có nếu thỏa quy tắc checked exception |
| Có thể áp dụng precise rethrow? | Có khi thỏa điều kiện phân tích kiểu | Có khi thỏa điều kiện | Có theo quy tắc phân tích kiểu tương ứng |
| `final` có đổi kiểu runtime không? | Không | Không | Không |

**Không nhầm:** `final` là quy tắc dành cho **biến**, không phải việc cấm ném ngoại lệ. Cũng không nên nói mọi `throw ex;` trong single-catch đều ném chính xác theo các ngoại lệ phát sinh bên trong `try`: điều đó phụ thuộc điều kiện phân tích và việc biến có bị gán lại hay không.

## 7. Vì sao *effectively final* quan trọng đối với lambda?

Biến cục bộ và tham số được **capture** từ phạm vi bên ngoài vào lambda cần là `final` hoặc *effectively final*.

Ví dụ single-catch hợp lệ:

```java
try {
    Integer.parseInt("abc");
} catch (NumberFormatException ex) {
    Runnable printer = () ->
            System.out.println(ex.getClass().getSimpleName());
    printer.run();
}
```

Stdout:

```text
NumberFormatException
```

Không có phép gán lại `ex`, nên lambda được dùng tham chiếu đó.

Nếu thêm phép gán vào `ex` trong handler:

```java
try {
    Integer.parseInt("abc");
} catch (NumberFormatException ex) {
    ex = new NumberFormatException("changed");
    Runnable printer = () -> System.out.println(ex.getMessage());
    printer.run();
}
```

Đoạn này **cố ý không biên dịch**: biến `ex` không còn *effectively final* nên không được capture từ lambda. Lỗi không phải do `Runnable` hay `NumberFormatException`, mà là do việc gán lại biến bị lambda tham chiếu.

Giải pháp rõ ràng hơn là **không gán lại ngoại lệ**, hoặc tạo một biến tách biệt:

```java
try {
    Integer.parseInt("abc");
} catch (NumberFormatException ex) {
    String description = "invalid number";
    Runnable printer = () -> System.out.println(description);
    printer.run();
}
```

Ở đây `description` là một biến cục bộ *effectively final*. Lambda đọc nó một cách hợp lệ. Nếu cần truyền cả ngoại lệ vào một tác vụ bất đồng bộ, phải xét thêm vòng đời dữ liệu, thread và chính sách bảo mật/logging; *effectively final* không tự bảo đảm thread safety của trạng thái bên trong đối tượng.

Với multi-catch, tham số `ex` ngầm `final`, nên việc capture không gặp trở ngại do gán lại chính biến đó. Điều này **không đồng nghĩa** tất cả đối tượng mà lambda sử dụng đều bất biến hoặc an toàn để dùng đồng thời.

## 8. Ví dụ Java 17+ hoàn chỉnh: gán lại single-catch và capture multi-catch

Lưu tên tệp **`CatchParameterDemo.java`**:

```java
import java.io.IOException;
import java.sql.SQLException;

public class CatchParameterDemo {

    static void perform(String mode) throws IOException, SQLException {
        switch (mode) {
            case "io" -> throw new IOException("disk unavailable");
            case "sql" -> throw new SQLException("database unavailable");
            default -> System.out.println("task: ok");
        }
    }

    static void single(String mode) {
        try {
            perform(mode);
        } catch (IOException ex) {
            IOException original = ex;

            // Single-catch không final: phép gán lại hợp lệ.
            ex = new IOException("context added", original);

            System.out.println("single: rebound=" + ex.getMessage());
            System.out.println("single: source=" + original.getMessage());

        } catch (final SQLException ex) {
            // ex = new SQLException("another"); // Cố ý không hợp lệ.
            System.out.println("single: sql=" + ex.getMessage());
        }
    }

    static void multi(String mode) {
        try {
            perform(mode);
        } catch (IOException | SQLException ex) {
            // ex = new IOException("another"); // Cố ý không hợp lệ.
            Runnable printer = () ->
                    System.out.println(
                            "multi: " + ex.getClass().getSimpleName()
                            + ": " + ex.getMessage());
            printer.run();
        }
    }

    static void capture(String text) {
        try {
            Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            // Không gán lại ex => effectively final.
            Runnable printer = () ->
                    System.out.println(
                            "captured: " + ex.getClass().getSimpleName());
            printer.run();
        }
    }

    public static void main(String[] args) {
        single("io");
        single("sql");
        multi("io");
        multi("sql");
        multi("ok");
        capture("abc");
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 CatchParameterDemo.java
java CatchParameterDemo
```

**Stdout chính xác:**

```text
single: rebound=context added
single: source=disk unavailable
single: sql=database unavailable
multi: IOException: disk unavailable
multi: SQLException: database unavailable
task: ok
captured: NumberFormatException
```

### Đọc hành vi trong ví dụ

**`single("io")`:** `perform` ném `IOException`. Trong handler đơn, `original` giữ tham chiếu tới lỗi gốc; `ex` được gán sang đối tượng mới. Vì handler chỉ in rồi kết thúc, đối tượng mới **không trở thành ngoại lệ bị ném ra khỏi `single`**. Đây là ví dụ minh họa tính hợp lệ của phép gán, không phải khuyến nghị xử lý lỗi theo cách này.

**`single("sql")`:** lỗi `SQLException` được bắt bởi handler có tham số `final SQLException ex`. Đọc thông điệp được; nếu bỏ comment dòng gán lại sẽ gây lỗi biên dịch.

**`multi("io")` và `multi("sql")`:** cùng một multi-catch xử lý hai loại checked exception không có quan hệ cha/con. Biến `ex` có kiểu tĩnh được suy ra phù hợp với cả hai lựa chọn, trong khi `ex.getClass().getSimpleName()` vẫn phản ánh loại **runtime thực tế**. Lambda được phép capture `ex`; code không và cũng không thể gán lại nó.

**`multi("ok")`:** không có ngoại lệ; handler không chạy, chỉ in `task: ok`.

**`capture("abc")`:** tham số single-catch không viết `final` nhưng không bị gán lại, nên là *effectively final* và được lambda sử dụng.

## 9. Precise rethrow là gì?

Đây là điểm dễ bị bỏ qua khi học `final` trong `catch`.

Thông thường, nếu ta có biến tham chiếu `Exception ex` rồi viết `throw ex;`, việc kiểm tra checked exception có thể bị hiểu là phải tính tới loại `Exception` rộng. Nhưng trong những điều kiện nhất định, Java phân tích được rằng tham số `catch` này chỉ có thể nhận **các ngoại lệ checked thực sự có thể phát sinh từ phần `try` tương ứng**, sau khi xét những handler đứng trước. Đó là **precise rethrow**, được giới thiệu từ Java 7.

Ví dụ:

```java
static void risky(String mode)
        throws java.io.IOException, java.sql.SQLException {
    if ("io".equals(mode)) {
        throw new java.io.IOException("disk error");
    }
    if ("sql".equals(mode)) {
        throw new java.sql.SQLException("query error");
    }
}

static void relay(String mode)
        throws java.io.IOException, java.sql.SQLException {
    try {
        risky(mode);
    } catch (Exception ex) {
        // ex không bị gán lại => effectively final.
        throw ex;
    }
}
```

Đây là **các phương thức minh họa**. `risky` có thể ném `IOException` hoặc `SQLException`, và `relay` **chỉ khai báo hai kiểu checked exception ấy**. Mặc dù tham số `catch` được khai báo là `Exception ex`, mã vẫn có thể biên dịch vì Java phân tích chính xác việc ném lại khi tham số không bị gán lại.

Nếu không có precise rethrow, người đọc có thể tưởng rằng `relay` buộc phải khai báo `throws Exception`. Đó không phải yêu cầu trong tình huống này.

### Điều kiện cần hiểu

Để được lợi từ phân tích precise rethrow cho tham số `catch` có kiểu rộng:

1. Ngoại lệ xuất phát từ các đường thực thi của `try` tương ứng có thể được phân tích theo luật kiểm tra ngoại lệ Java.
2. `ex` là tham số của `catch` đang bắt ngoại lệ đó.
3. Tham số không bị gán lại theo cách làm mất điều kiện `final`/*effectively final*.
4. Những kiểu checked exception còn có thể ném ra phải được bắt hoặc khai báo trong phương thức chứa `throw ex;`.

Nếu một `catch (Exception ex)` bắt cả unchecked exceptions, việc phân tích checked exception vẫn phải tuân theo quy tắc ngôn ngữ; không phải cứ có `catch (Exception)` là mọi phương thức đều tự động được khai báo bất kỳ kiểu nào.

**Quan trọng:** precise rethrow là **kiểm tra tại thời điểm biên dịch**; nó không “biến” đối tượng `IOException` thành `SQLException` hay thay đổi `getClass()` lúc chạy.

## 10. Ví dụ Java 17+ hoàn chỉnh: precise rethrow với tham số effectively final

Lưu tên tệp **`EffectivelyFinalRethrowDemo.java`**:

```java
import java.io.IOException;
import java.sql.SQLException;

public class EffectivelyFinalRethrowDemo {

    static void risky(String mode) throws IOException, SQLException {
        if ("io".equals(mode)) {
            throw new IOException("storage unavailable");
        }
        if ("sql".equals(mode)) {
            throw new SQLException("database unavailable");
        }
    }

    static void relay(String mode) throws IOException, SQLException {
        try {
            risky(mode);
        } catch (Exception ex) {
            System.out.println(
                    "relay: " + ex.getClass().getSimpleName());

            // ex effectively final, nên precise rethrow áp dụng.
            throw ex;
        }
    }

    static void runCase(String mode) {
        System.out.println("mode=" + mode);

        try {
            relay(mode);
            System.out.println("result=ok");
        } catch (IOException ex) {
            System.out.println("io: " + ex.getMessage());
        } catch (SQLException ex) {
            System.out.println("sql: " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        runCase("io");
        runCase("sql");
        runCase("ok");
    }
}
```

Lệnh chạy:

```bash
javac -encoding UTF-8 EffectivelyFinalRethrowDemo.java
java EffectivelyFinalRethrowDemo
```

**Stdout chính xác:**

```text
mode=io
relay: IOException
io: storage unavailable
mode=sql
relay: SQLException
sql: database unavailable
mode=ok
result=ok
```

### Phân tích từng đường chạy

- `risky("io")` ném `IOException`. `relay` bắt qua tham số kiểu rộng `Exception`, in loại thực tế rồi ném **cùng đối tượng** bằng `throw ex;`. `runCase` bắt `IOException`.
- `risky("sql")` tương tự với `SQLException`, và caller chọn handler `SQLException`.
- `risky("ok")` không ném; `catch` trong `relay` không chạy, nên `runCase` in `result=ok`.
- Chữ ký `relay(String mode) throws IOException, SQLException` là hợp lệ, **không cần đổi thành `throws Exception`**, vì tham số `ex` chưa hề được gán lại và trình biên dịch có thể xác định những loại checked exception từ `try`.

Ví dụ cho thấy sự khác nhau giữa **bắt bằng một kiểu cha để thực hiện một hành động chung** và **bảo toàn các kiểu checked exception của hợp đồng khi ném lại**.

Trong dự án thật, một handler chỉ để `throw ex;` thường thừa. Precise rethrow có giá trị khi handler cần làm việc có chủ đích trước khi chuyển lỗi tiếp: ghi nhận trạng thái tác vụ, bổ sung sự kiện giám sát hoặc quản lý một chính sách cục bộ.

## 11. Gán lại tham số `catch` có thể làm mất precise rethrow

Xem phiên bản **cố ý không biên dịch** sau:

```java
static void relayBad(String mode)
        throws java.io.IOException, java.sql.SQLException {
    try {
        risky(mode);
    } catch (Exception ex) {
        ex = new Exception("replacement");
        throw ex; // Lỗi biên dịch: Exception checked chưa được khai báo.
    }
}
```

Đây là **mã cố ý sai**, giả định phương thức `risky` ở ví dụ phía trước tồn tại và khai báo `throws IOException, SQLException`.

Nguyên nhân:

1. `catch (Exception ex)` đơn không viết `final` cho phép **gán lại** tham số.
2. Sau phép gán `ex = new Exception(...)`, tham số `ex` **không còn effectively final**.
3. Trình biên dịch không thể áp dụng phân tích ném lại chính xác như trước. Biểu thức `throw ex` lúc này cần được kiểm tra theo kiểu tĩnh rộng `Exception`.
4. Chữ ký `throws IOException, SQLException` không bao phủ mọi checked `Exception`, vì vậy báo lỗi.

Các cách sửa tùy ý định:

- **Không gán lại `ex`** nếu chỉ muốn chuyển tiếp đúng lỗi gốc; giữ `throw ex;`.
- **Khai báo `final Exception ex`** để người viết vô tình gán lại sẽ được báo lỗi ngay tại phép gán.
- Nếu thật sự muốn **ném ngoại lệ mới** kiểu `Exception`, khai báo/catch phù hợp theo hợp đồng, hoặc chọn một kiểu cụ thể có ý nghĩa và giữ ngoại lệ gốc làm `cause`.
- **Đừng mở chữ ký thành `throws Exception` chỉ để che thiết kế lỗi không rõ ràng**, nếu caller chỉ cần xử lý `IOException` và `SQLException`.

Lưu ý tinh tế: gán lại một tham số `catch (IOException ex)` cũng làm mất *effectively final*, nhưng `throw ex;` vẫn có thể hợp lệ trong phương thức `throws IOException`, bởi **kiểu tĩnh của `ex` vốn đã là `IOException`**. Vấn đề precise rethrow biểu hiện rõ nhất khi handler dùng **kiểu khai báo rộng hơn các checked exception có thể thực sự phát sinh từ `try`**.

## 12. `final` tường minh trong single-catch có tác dụng thế nào?

Thay vì:

```java
catch (Exception ex) {
    throw ex;
}
```

có thể viết:

```java
catch (final Exception ex) {
    throw ex;
}
```

Trong bối cảnh `try` và chữ ký phù hợp, cả hai đều có thể hưởng phân tích precise rethrow. Khác biệt chính là:

- `final` tường minh làm **mọi phép gán lại `ex` bị báo lỗi ngay**.
- Không viết `final` nhưng giữ *effectively final* vẫn đủ cho các quy tắc liên quan.
- Dùng `final` thể hiện chủ ý rõ ràng rằng biến tham chiếu không nên bị thay đổi.
- `final` **không** tự khiến một handler kiểu rộng trở thành cách xử lý tốt: nó không quyết định chính sách phục hồi, không bảo vệ dữ liệu nhạy cảm và không thay thế việc chọn loại lỗi thích hợp.

Trong Java hiện đại, có thể chọn phong cách dự án: khai báo `final` cho các tham số quan trọng, hoặc dựa vào *effectively final*. Nhưng **không nên gán lại tham số bắt lỗi chỉ vì cú pháp cho phép**.

## 13. `catch` kiểu rộng, thứ tự các handler và giới hạn precise rethrow

Giả sử `try` có thể ném cả `IOException` và `SQLException`. Một handler cụ thể có thể xử lý một phần, còn handler sau ném lại phần còn lại:

```java
static void caller() throws java.sql.SQLException {
    try {
        performDatabaseAndFileOperation();
    } catch (java.io.IOException ex) {
        System.out.println("I/O issue has been handled");
    } catch (Exception ex) {
        throw ex;
    }
}
```

Đây là **các khai báo minh họa**, giả định `performDatabaseAndFileOperation()` có `throws IOException, SQLException` và không có checked exception khác. Khi handler `IOException` trước đã xử lý nhóm I/O, handler `catch (Exception ex)` sau chỉ cần ném lại nhóm checked còn lại `SQLException`, nên khai báo `throws SQLException` của `caller` có thể đủ theo quy tắc precise rethrow.

Không đảo thứ tự thành `catch (Exception)` trước `catch (IOException)`: handler lớp cha phía trước sẽ khiến handler lớp con không thể tới được.

**Vì sao điều này quan trọng?** Trình biên dịch không chỉ nhìn một dòng `throw ex;` mà còn phân tích các loại checked exception trong phần `try` và những handler đã bắt chúng trước đó. Đây là lý do không nên đơn giản hóa precise rethrow thành câu “`catch (Exception)` luôn đòi `throws Exception`” hay “`catch (Exception)` luôn có thể khai báo tùy ý”.

Nếu chưa chắc kiểu nào có thể thoát ra, hãy biên dịch một ví dụ độc lập và kiểm tra chữ ký, thay vì đổi ngẫu nhiên toàn bộ các loại `throws` thành `Exception`.

## 14. Multi-catch và việc ném lại

Một multi-catch có thể ném lại đối tượng:

```java
static void relayMulti(String mode)
        throws java.io.IOException, java.sql.SQLException {
    try {
        risky(mode);
    } catch (java.io.IOException | java.sql.SQLException ex) {
        throw ex;
    }
}
```

Đoạn này là **phương thức minh họa**, giả định `risky(mode)` đã được định nghĩa với cùng hợp đồng checked exception. `IOException` và `SQLException` là hai lớp không có quan hệ cha/con, nên multi-catch hợp lệ. `ex` ngầm `final`; `throw ex;` giữ đối tượng lỗi thực tế và cho phép hợp đồng liệt kê hai loại checked exception.

Sự khác nhau cần nhớ:

- Trong **multi-catch**, không thể gán lại tham số dù bạn **không** ghi `final`. Đây là quy tắc trực tiếp của ngôn ngữ.
- Trong **single-catch**, muốn ngăn gán lại có thể ghi `final`; nếu không ghi và không gán thì biến vẫn effectively final.
- Cả hai hình thức đều có thể ném lại lỗi; việc cho phép về mặt biên dịch vẫn phụ thuộc hợp đồng checked exception.
- `throw ex;` không tạo wrapper, nên không tự bổ sung thông điệp/ngữ cảnh. Nếu cần đổi loại ngoại lệ, tạo đối tượng mới có `cause` và xử lý hợp đồng theo kiểu ngoại lệ mới.

### Multi-catch không thay đổi kiểu runtime

Nếu `risky` ném `SQLException`, tham số `ex` ở multi-catch tham chiếu tới một **đối tượng `SQLException` thực tế**. `ex.getClass().getSimpleName()` trả về `SQLException`, dù handler được chia sẻ với `IOException`.

Không nên nhầm kiểu tĩnh chung của tham số với việc chuyển đổi đối tượng lúc chạy. Việc `ex` là ngầm `final` cũng chỉ hạn chế thay đổi **tham chiếu**, không biến ngoại lệ thành bất biến.

## 15. Những lỗi lập trình thường gặp

| Sai lầm | Tại sao sai | Cách sửa |
| --- | --- | --- |
| Nghĩ mọi tham số `catch` đều tự động `final` | Single-catch không có `final` cho phép gán lại | Phân biệt single-catch và multi-catch |
| Nghĩ multi-catch có thể gán lại `ex` vì không viết `final` | Tham số multi-catch ngầm `final` | Tạo biến mới nếu cần xử lý giá trị khác |
| Gán `ex = new Exception(...)` rồi `throw ex` trong handler `catch (Exception)` và chỉ khai báo `throws IOException, SQLException` | Mất *effectively final*, làm mất precise rethrow | Không gán lại hoặc dùng hợp đồng checked phù hợp |
| Bắt lỗi rồi chỉ gán `ex` sang đối tượng mới | Không ném ngoại lệ mới cho caller | `throw new SomeException("context", ex)` nếu muốn chuyển lỗi |
| Dùng biến `catch` đã gán lại trong lambda | Biến không còn *effectively final* | Giữ tham số không gán lại hoặc capture giá trị khác |
| Hiểu `final` khiến đối tượng `Throwable` bất biến | `final` chỉ khóa việc gán lại tham chiếu | Phân biệt tham chiếu và trạng thái đối tượng |
| Dùng `catch (IOException | FileNotFoundException)` | Có quan hệ cha/con giữa hai lựa chọn | Dùng `catch (IOException)` hoặc handler riêng |
| Đặt `catch (Exception)` trước handler subtype | Handler sau bị che khuất | Đặt kiểu cụ thể trước kiểu cha |
| Nghĩ `throw ex;` tự tạo stack trace/cause mới | Đây là ném lại cùng đối tượng | Tạo wrapper có cause nếu thật sự cần thêm ngữ cảnh |
| Bắt `Exception` ở mọi tầng chỉ để ghi log rồi ném | Tạo nhiễu, che ranh giới trách nhiệm | Bắt ở tầng có hành động hữu ích |

### Tránh nhầm “đã bị bắt” với “được xử lý xong”

Một `catch` có thể làm ba việc rất khác nhau:

```java
// A. Phục hồi có chủ đích và hoàn tất bình thường.
catch (KnownRecoverableException ex) {
    useVerifiedFallback();
}

// B. Ném lại cùng đối tượng.
catch (KnownRecoverableException ex) {
    throw ex;
}

// C. Bọc với ngữ cảnh mới và giữ nguyên nhân.
catch (KnownRecoverableException ex) {
    throw new HigherLevelException("operation failed", ex);
}
```

Các đoạn trên **chỉ minh họa**, cần định nghĩa các kiểu/phương thức cho ứng dụng thực; không phải tệp Java độc lập. Ở trường hợp A, caller không nhận ngoại lệ nếu fallback thành công. Ở B, caller nhận cùng đối tượng. Ở C, caller nhận đối tượng mới và có thể truy nguyên theo `getCause()`.

Sự lựa chọn này liên quan **chính sách lỗi và ranh giới xử lý**, không phải do tham số `catch` có `final` hay không.

## 16. Checklist khi dùng `final` và precise rethrow

Khi đọc hoặc sửa một khối `catch`, hãy kiểm tra:

1. **Đây là single-catch hay multi-catch?** Multi-catch có tham số ngầm `final`; single-catch chỉ `final` nếu khai báo tường minh.
2. **Có gán lại biến ngoại lệ không?** Tìm `ex = ...`, kể cả trong nhánh điều kiện. Nếu có, single-catch không còn *effectively final*.
3. **Có lambda/lớp ẩn danh capture tham số này không?** Biến capture phải `final` hoặc *effectively final*.
4. **Có `throw ex;` trong handler kiểu rộng không?** Nếu có, kiểm tra precise rethrow và các loại checked exception được phân tích từ phần `try`.
5. **Chữ ký `throws` đã bao phủ đúng các checked exception chưa?** Không tự ý mở thành `throws Exception` khi phần `try` thực chất chỉ có `IOException` và `SQLException`.
6. **Ngoại lệ nào đang được ném thật?** Gán lại tham số không biến đổi đối tượng cũ; ném lại có nghĩa khác với tạo wrapper.
7. **Có cần giữ `cause` không?** Khi chuyển sang ngoại lệ mới, truyền lỗi gốc qua constructor nhận cause nếu phù hợp.
8. **Multi-catch có các loại cha/con trùng nhau không?** Nếu có, tách hoặc rút gọn các lựa chọn.
9. **Có thể làm đơn giản hơn không?** Nếu handler chỉ `throw ex;` và không có công việc có ý nghĩa, để ngoại lệ tự lan truyền thường rõ hơn.
10. **Đã thử biên dịch các đường lỗi quan trọng chưa?** Đặc biệt các ví dụ gán lại tham số và phương thức khai báo checked exception hẹp.

## 17. Câu hỏi tự kiểm tra

**Câu 1.** `catch (IOException ex)` có cho phép `ex = new IOException("other");` không?

**Có**, nếu đây là single-catch và tham số không khai báo `final`. Tuy nhiên, phép gán khiến `ex` không còn *effectively final*, và không tự ném ngoại lệ mới.

**Câu 2.** `catch (IOException | SQLException ex)` có cho phép gán lại `ex` không?

**Không.** Multi-catch có tham số ngầm `final`.

**Câu 3.** `catch (Exception ex) { throw ex; }` có nhất thiết buộc phương thức khai báo `throws Exception` không?

**Không.** Khi `ex` không bị gán lại và các điều kiện precise rethrow thỏa mãn, trình biên dịch có thể xác định tập checked exception hẹp hơn từ phần `try`, ví dụ `IOException` và `SQLException`.

**Câu 4.** Nếu viết `ex = new Exception("new"); throw ex;` trong handler `catch (Exception ex)` thì sao?

Tham số không còn *effectively final*. Với một phương thức chỉ `throws IOException, SQLException`, lỗi kiểu checked `Exception` rộng hơn có thể gây lỗi biên dịch. Muốn ném đối tượng mới thì cần thiết kế hợp đồng phù hợp, chứ không dựa vào precise rethrow.

**Câu 5.** Một tham số `catch` không có từ khóa `final` nhưng không hề bị gán lại có thể được lambda sử dụng không?

**Có.** Nó là *effectively final* theo quy tắc của Java.

**Câu 6.** `final IOException ex` có ngăn code gọi `ex.getMessage()` hoặc `throw ex;` không?

**Không.** `final` cấm gán lại biến `ex`, không cấm đọc tham chiếu hoặc ném cùng đối tượng.

**Câu 7.** Gán `ex = new IOException("context", original)` có tự làm caller nhận lỗi mới không?

**Không.** Nếu handler kết thúc bình thường mà không `throw`, caller không nhận ngoại lệ mới chỉ vì có phép gán.

**Câu 8.** Multi-catch có thể chứa `NumberFormatException | IllegalArgumentException` không?

**Không.** Hai lựa chọn có quan hệ kế thừa; `NumberFormatException` là lớp con của `IllegalArgumentException`.

**Kết luận:** tham số **single-catch** có thể được gán lại nếu không khai báo `final`; tham số **multi-catch** thì ngầm `final`. Một tham số single-catch **không bị gán lại** là *effectively final*, có thể được lambda capture và giúp trình biên dịch áp dụng **precise rethrow** trong những tình huống thích hợp. Khi cần chuyển lỗi, hãy chọn hành động rõ ràng—xử lý, ném lại hoặc bọc có `cause`—thay vì gán lại biến ngoại lệ một cách khó hiểu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multi-catch-rethrow-008">So sánh multi-catch với catch kiểu cha quá rộng</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh multi-catch với `catch` kiểu cha quá rộng — Java Core Exception

> **Bối cảnh:** Java Core → Multi-catch và rethrow. Khi nhiều loại ngoại lệ cần cùng một hành động xử lý, `catch (IOException | SQLException e)` thường thể hiện ý định rõ hơn `catch (Exception e)`. Hai cách có thể bắt cùng những lỗi được dự kiến, nhưng **phạm vi bắt và hệ quả đối với lỗi không dự kiến rất khác nhau**. Bài này tập trung vào cách chọn handler, quy tắc biên dịch, biến multi-catch, ném lại ngoại lệ và cách quyết định handler nào là hợp lý.

## 1. Câu trả lời nhanh

Xét một lời gọi có thể phát sinh hai loại checked exception:

```java
// Minh họa trong method; performOperation() có thể ném
// IOException hoặc SQLException.
try {
    performOperation();
} catch (java.io.IOException | java.sql.SQLException e) {
    reportFailure(e);
}
```

So với:

```java
try {
    performOperation();
} catch (Exception e) {
    reportFailure(e);
}
```

Hai đoạn đều là **minh họa**, vì `performOperation()` và `reportFailure(e)` chưa được định nghĩa. Khác biệt quan trọng:

- **Multi-catch:** chỉ bắt `IOException`, `SQLException` **và các lớp con của chúng**. Một lỗi lập trình như `NullPointerException` hoặc `IllegalStateException` sẽ không bị handler này nuốt và có thể lan ra tầng cao hơn.
- **`catch (Exception)`:** cũng bắt `IOException` và `SQLException`, nhưng còn bắt **mọi lớp con của `Exception`**, bao gồm phần lớn unchecked exceptions như `NullPointerException`, `NumberFormatException` và `IllegalStateException`.
- **Cả hai đều không mặc nhiên bắt `Error`.** `Error` là nhánh khác của `Throwable`; nếu viết `catch (Throwable)` thì phạm vi còn rộng hơn, thường không phù hợp cho ứng dụng thông thường.
- **Độ rõ ràng của hợp đồng:** multi-catch nói chính xác những nhóm thất bại mà handler dự định xử lý giống nhau. `catch (Exception)` không cho người đọc biết nhóm lỗi nào đã được cân nhắc.
- **Về checked exception:** Java có quy tắc **precise rethrow**; không nên nói rằng `catch (Exception)` *luôn* buộc phải khai báo `throws Exception` khi ném lại. Phần sau có ví dụ để phân biệt chính xác.

### 1.1. Bảng so sánh

| Tiêu chí | `catch (IOException \| SQLException e)` | `catch (Exception e)` |
| --- | --- | --- |
| Phạm vi theo kiểu | Hai nhánh được nêu và các subtype | Toàn bộ nhánh `Exception` |
| Bắt lỗi I/O được dự kiến | Có | Có |
| Bắt lỗi SQL được dự kiến | Có | Có |
| Bắt `NullPointerException` | Không | **Có** |
| Bắt `IllegalStateException` | Không | **Có** |
| Bắt `AssertionError` | Không | Không |
| Nói rõ ý định xử lý | Có | Ít rõ hơn |
| Có thể nuốt lỗi lập trình không dự kiến | Ít hơn; những lỗi ngoài tập khai báo lan tiếp | **Có**, nếu handler xử lý chung/nuốt lỗi |
| Biến `e` được gán lại? | **Không**; implicitly final | Có thể gán lại về mặt cú pháp, nhưng làm mất một số ưu điểm phân tích chính xác khi rethrow |
| Có thể ném lại? | Có | Có; xem precise rethrow |
| Khi nào nên dùng? | Những exception cụ thể cần cùng logic | Ranh giới ứng dụng có chính sách xử lý tổng quát thật sự |

**Kết luận thiết kế:** lựa chọn handler nên bắt đầu từ “tầng này thực sự biết xử lý loại lỗi nào?”, không phải “viết cái nào ít ký tự hơn?”.

## 2. Vì sao multi-catch là một lựa chọn có chủ đích?

Trước Java 7, nếu hai loại exception cần cùng logic, người viết thường lặp:

```java
try {
    work();
} catch (java.io.IOException e) {
    log(e);
    markFailed();
} catch (java.sql.SQLException e) {
    log(e);
    markFailed();
}
```

Đây là đoạn minh họa; các method chưa được định nghĩa. Logic `log` và `markFailed` bị lặp. Từ Java 7, có thể dùng multi-catch:

```java
try {
    work();
} catch (java.io.IOException | java.sql.SQLException e) {
    log(e);
    markFailed();
}
```

Lợi ích là **gom phần xử lý giống nhau mà không mở rộng nhóm ngoại lệ**. Về ý nghĩa, multi-catch là một cách biểu diễn ngắn cho cùng một hành động xử lý áp dụng cho **nhiều kiểu độc lập**, thay vì giả định mọi `Exception` đều có thể xử lý như nhau.

Không nên thay hai `catch` cụ thể bằng một `catch (Exception)` chỉ để tránh lặp code. Một handler tổng quát sẽ thay đổi tập lỗi bị giữ lại, không chỉ thay đổi cách trình bày.

### 2.1. Multi-catch không phải cơ chế bắt “tất cả các exception”

Cú pháp:

```java
catch (TypeA | TypeB | TypeC e) {
    // Cùng một hành động xử lý cho từng loại.
}
```

`TypeA`, `TypeB`, `TypeC` phải là các kiểu exception hợp lệ thuộc hệ `Throwable` và thỏa điều kiện ngôn ngữ về các lựa chọn trong multi-catch. Dấu ngăn cách là một dấu **`|`**, không phải `||`. Đây không phải cú pháp kiểm tra boolean trong `if`.

Khi ngoại lệ được ném, Java chọn multi-catch nếu đối tượng runtime tương thích với **ít nhất một** kiểu được liệt kê. Handler chạy **một lần**, không chạy lần lượt theo số lượng lựa chọn.

### 2.2. Phạm vi bắt được xác định bằng quan hệ kế thừa

```text
Throwable
├── Error
│   └── AssertionError
└── Exception
    ├── IOException
    │   ├── FileNotFoundException
    │   └── EOFException
    ├── SQLException
    └── RuntimeException
        ├── IllegalStateException
        ├── NullPointerException
        └── IllegalArgumentException
            └── NumberFormatException
```

Cây này chỉ có các lớp tiêu biểu.

Với `catch (IOException | SQLException e)`:

- `new FileNotFoundException(...)` được bắt vì là subtype của `IOException`.
- `new SQLException(...)` được bắt trực tiếp.
- `new NumberFormatException(...)` **không** được bắt vì nằm dưới `RuntimeException`, ngoài hai nhánh đã chọn.
- `new AssertionError(...)` cũng không được bắt.

Với `catch (Exception e)`, ba trường hợp đầu đều được bắt (vì `IOException`, `SQLException` và `RuntimeException` đều là subtype của `Exception`). `AssertionError` vẫn không được bắt.

## 3. Chương trình Java 17: khác biệt nhìn thấy được khi lỗi lập trình xuất hiện

Để so sánh công bằng, hai phương thức sau gọi **cùng một** hoạt động. Handler multi-catch chỉ nhận hai loại lỗi hạ tầng đã dự kiến; handler tổng quát bắt cả lỗi trạng thái nằm ngoài kế hoạch xử lý.

Tạo tệp `CatchScopeDemo.java`:

```java
import java.io.IOException;
import java.sql.SQLException;

public class CatchScopeDemo {
    static void execute(String mode) throws IOException, SQLException {
        switch (mode) {
            case "io" -> throw new IOException("Nguồn đọc lỗi");
            case "db" -> throw new SQLException("Truy vấn lỗi");
            case "bug" -> throw new IllegalStateException("Trạng thái sai");
            default -> System.out.println("Thao tác thành công");
        }
    }

    static void targeted(String mode) {
        try {
            execute(mode);
        } catch (IOException | SQLException e) {
            System.out.println("multi: "
                    + e.getClass().getSimpleName());
        }
    }

    static void broad(String mode) {
        try {
            execute(mode);
        } catch (Exception e) {
            System.out.println("broad: "
                    + e.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        targeted("io");
        targeted("db");

        try {
            targeted("bug");
        } catch (IllegalStateException e) {
            System.out.println("caller: "
                    + e.getClass().getSimpleName());
        }

        broad("bug");
        targeted("ok");
    }
}
```

Biên dịch/chạy với JDK 17+:

```bash
javac -encoding UTF-8 CatchScopeDemo.java
java CatchScopeDemo
```

Stdout:

```text
multi: IOException
multi: SQLException
caller: IllegalStateException
broad: IllegalStateException
Thao tác thành công
```

Giải thích:

1. `targeted("io")` ném `IOException`, multi-catch nhận và in `multi: IOException`.
2. `targeted("db")` ném `SQLException`, handler đó cũng nhận vì đã nêu rõ lựa chọn thứ hai.
3. `targeted("bug")` ném unchecked `IllegalStateException`. Nó **không** thuộc `IOException` hoặc `SQLException` nên lan ra `main`, nơi có handler riêng.
4. `broad("bug")` bắt cùng lỗi đó bằng `catch (Exception)`. Ở ví dụ này ta cố ý in ra để quan sát; trong hệ thống thật, nếu handler chung đánh dấu “đã xử lý xong”, lỗi lập trình có thể bị che.
5. `targeted("ok")` kết thúc bình thường và in `Thao tác thành công`.

**Đây là khác biệt quan trọng nhất:** khi danh sách lỗi cần xử lý đã biết, multi-catch thường bảo vệ việc phân biệt “lỗi đã có phương án xử lý” với “lỗi không dự kiến cần được đẩy lên”. Nó không tự động khôi phục hệ thống, nhưng tránh thu gom vô tình các nhánh không thuộc hợp đồng.

### 3.1. Phân biệt lỗi không dự kiến với lỗi luôn phải dừng

Không được suy ra rằng mọi `RuntimeException` đều là bug và không bao giờ cần xử lý. `NumberFormatException` từ dữ liệu nhập người dùng có thể được bắt ở tầng UI để yêu cầu nhập lại; `IllegalArgumentException` trong code nghiệp vụ có thể biểu thị vi phạm hợp đồng caller. Vấn đề của `catch (Exception)` là nó **không phân loại**: bắt cả những tình huống cần khôi phục và những bug mà tầng hiện tại không biết cách xử lý.

Ngược lại, cũng không khẳng định mọi checked `IOException` hoặc `SQLException` đều phục hồi được. Bắt một exception đúng kiểu chưa chứng minh trạng thái dữ liệu còn hợp lệ, giao dịch đã rollback hay thao tác có thể thử lại an toàn.

## 4. Những ràng buộc compile-time của multi-catch

Một union như `catch (IOException | SQLException ex)` cần các alternative không có quan hệ subtype và những checked exception hợp lệ với các đường ném của `try`. Hai phản ví dụ:

```java
// Sai: FileNotFoundException là subtype của IOException.
catch (IOException | java.io.FileNotFoundException ex) {
    reportFailure(ex);
}
```

```java
// Sai: parseInt không phát sinh checked IOException.
try {
    Integer.parseInt("abc");
} catch (java.io.IOException | NumberFormatException ex) {
    reportFailure(ex);
}
```

Hai đoạn chỉ minh họa lỗi biên dịch, không phải chương trình độc lập. Với `parseInt`, bắt riêng `NumberFormatException`; với cặp cha–con, dùng `catch (IOException ex)` hoặc tách các handler theo thứ tự con trước cha. Những giới hạn cú pháp này đáng kiểm tra, nhưng lựa chọn **multi-catch hay `catch (Exception)`** còn quan trọng hơn: handler chung có được phép **tiêu thụ lỗi ngoài phạm vi dự kiến** hay không?

## 5. Biến `e` trong multi-catch có đặc điểm gì?

`catch (IOException | SQLException e)` khai báo **một** biến tham chiếu ngoại lệ, ngầm `final`. Kiểu tĩnh của `e` được suy luận theo **least upper bound** của các alternative: có thể dùng `getMessage()`, `getCause()`, `printStackTrace()` chung, nhưng không thể gọi trực tiếp `SQLException.getSQLState()` mà không thu hẹp kiểu.

```java
catch (java.io.IOException | java.sql.SQLException e) {
    System.out.println(e.getClass().getSimpleName());
    if (e instanceof java.sql.SQLException sql) {
        System.out.println(sql.getSQLState());
    }
}
```

Đây là nhánh minh họa trong `try` hợp lệ. Trong một handler rộng như `catch (Exception e)`, kiểu tĩnh chỉ là `Exception`, nhưng đối tượng runtime cũng không bị đổi loại. **Khác biệt quyết định giữa hai handler nằm ở tập lỗi bị bắt**, không phải việc dùng được `getMessage()` hay không.

## 6. Multi-catch và rethrow: chỉ khai báo các checked exception phù hợp

Multi-catch vẫn có thể **ném lại đúng đối tượng** đang bắt:

```java
static void forward() throws java.io.IOException, java.sql.SQLException {
    try {
        fetch();
    } catch (java.io.IOException | java.sql.SQLException e) {
        throw e;
    }
}
```

Đây là phương thức minh họa, yêu cầu `fetch()` được định nghĩa và có thể ném hai loại checked exception. Nếu caller cần phân biệt `IOException` và `SQLException`, `throw e` giữ nguyên loại runtime. Nhưng ưu điểm trung tâm của handler hẹp trong bài này là **không bắt nhầm lỗi lập trình `IllegalStateException`**; lỗi đó tiếp tục đi tới ranh giới giám sát, thay vì bị handler rộng coi như một thất bại hạ tầng đã phục hồi.

## 7. Điểm tinh tế: `catch (Exception)` cũng có thể precise rethrow

**Không đúng** nếu khẳng định `catch (Exception e) { throw e; }` luôn bắt buộc `throws Exception`. Java 7+ có **precise rethrow**: nếu `e` là tham số `catch` *effectively final* và compiler xác định `try` chỉ có thể ném `IOException`, `SQLException` trong nhóm checked exception đang xét, thì hai loại đó có thể đủ cho chữ ký:

```java
static void withBroadCatch()
        throws java.io.IOException, java.sql.SQLException {
    try {
        fetch();
    } catch (Exception e) {
        throw e;
    }
}
```

Đây là minh họa với `fetch()` được khai báo `throws IOException, SQLException`. **Kiểu được phép khai báo trong `throws`** và **tập exception bị handler bắt tại runtime** là hai khía cạnh khác nhau. Multi-catch chỉ bắt những nhánh được chỉ danh; `catch (Exception)` còn bắt nhiều `RuntimeException` chưa dự kiến. Nếu gán lại `e` trong single-catch, khả năng precise rethrow có thể mất. Vì vậy hãy chọn handler theo **trách nhiệm phục hồi**, không theo giả định sai rằng `catch (Exception)` luôn khiến chữ ký rộng hơn.

## 8. Khi nào dùng các `catch` riêng thay vì multi-catch?

Multi-catch chỉ nên gộp các loại exception khi **cùng chiến lược xử lý** thực sự phù hợp. Nếu phương án khắc phục khác nhau, tách handler rõ ràng hơn:

- `IOException`: có thể chọn nguồn dữ liệu khác hoặc báo file không đọc được.
- `SQLException`: có thể ghi nhận lỗi truy cập database, đánh dấu tác vụ cần kiểm tra giao dịch, quyết định dừng.
- `NumberFormatException`: có thể yêu cầu người dùng sửa chuỗi nhập.

Một multi-catch rồi phân nhánh bằng nhiều `instanceof` có thể trở thành phiên bản vòng vo của các handler riêng.

### 8.1. Chương trình Java 17: hai cách phục hồi khác nhau

Tạo `SeparateCatchPoliciesDemo.java`:

```java
import java.io.IOException;
import java.sql.SQLException;

public class SeparateCatchPoliciesDemo {
    static String load(String source) throws IOException, SQLException {
        if ("file".equals(source)) {
            throw new IOException("Tệp tạm không đọc được");
        }
        if ("db".equals(source)) {
            throw new SQLException("Database không sẵn sàng");
        }
        return "Dữ liệu mới";
    }

    static void run(String source) {
        try {
            System.out.println("Kết quả: " + load(source));
        } catch (IOException e) {
            System.out.println("I/O: dùng bản sao lưu");
        } catch (SQLException e) {
            System.out.println("DB: dừng yêu cầu");
        }
    }

    public static void main(String[] args) {
        run("file");
        run("db");
        run("memory");
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 SeparateCatchPoliciesDemo.java
java SeparateCatchPoliciesDemo
```

Stdout:

```text
I/O: dùng bản sao lưu
DB: dừng yêu cầu
Kết quả: Dữ liệu mới
```

Ở đây mỗi exception cần **một hành động khác nhau**. Ép cả hai vào multi-catch rồi kiểm tra `instanceof` trong handler sẽ làm mã dài và khó theo dõi hơn. `catch (Exception)` còn ít phù hợp hơn vì nó cũng nhận các lỗi ngoài hai nhóm này.

**Lưu ý:** thông điệp “dùng bản sao lưu” chỉ mô phỏng quyết định; ví dụ không chứa triển khai backup thật. Trong chương trình thực tế phải bảo đảm fallback thực sự có dữ liệu và phù hợp tính nhất quán của hệ thống.

## 9. Khi nào `catch (Exception)` lại là lựa chọn hợp lý?

Không nên tuyệt đối hóa quy tắc “luôn dùng multi-catch, không bao giờ catch Exception”. Có những **ranh giới cấp cao** mà mục đích chính là ngăn thất bại của một tác vụ làm hỏng luồng điều phối những tác vụ khác. Ví dụ:

- một worker xử lý job, mỗi job có phạm vi trạng thái độc lập và được đánh dấu `FAILED` nếu exception thoát ra;
- một CLI command cần chuyển exception không dự kiến thành thông báo lỗi tổng quát và exit code khác 0;
- một request boundary có cơ chế log và chuyển lỗi thành phản hồi lỗi thích hợp, đồng thời không xem request thất bại là thành công;
- một bộ giám sát cần thu thập chẩn đoán cho mọi `Exception` thoát khỏi một thao tác.

Với các trường hợp ấy, `catch (Exception)` có thể phù hợp **nếu có chính sách rõ ràng**: ghi nhận/đánh dấu failure, dọn tài nguyên, không dùng tiếp trạng thái không đáng tin, có giám sát và không nuốt lỗi.

### 9.1. Ví dụ Java 17: bắt rộng đúng tại boundary và báo thất bại

Lưu `BoundaryCatchDemo.java`:

```java
import java.io.IOException;

public class BoundaryCatchDemo {
    static void execute(String kind) throws IOException {
        switch (kind) {
            case "io" -> throw new IOException("Không thể đọc");
            case "bug" -> throw new IllegalStateException("Sai trạng thái");
            default -> System.out.println("Nội dung đã xử lý");
        }
    }

    static boolean runJob(String kind) {
        try {
            execute(kind);
            return true;
        } catch (Exception e) {
            System.out.println("Job thất bại: "
                    + e.getClass().getSimpleName());
            return false;
        }
    }

    public static void main(String[] args) {
        System.out.println("io success=" + runJob("io"));
        System.out.println("bug success=" + runJob("bug"));
        System.out.println("ok success=" + runJob("ok"));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 BoundaryCatchDemo.java
java BoundaryCatchDemo
```

Stdout:

```text
Job thất bại: IOException
io success=false
Job thất bại: IllegalStateException
bug success=false
Nội dung đã xử lý
ok success=true
```

Khác với một handler nuốt lỗi, `runJob` trả `false` để thông báo rõ thất bại của tác vụ. Ví dụ **không** bảo đảm an toàn với mọi môi trường: công việc có side effect cần transaction/compensation nếu muốn khôi phục; tác vụ nhiều luồng cần cơ chế đồng bộ; handler còn cần chính sách log và phân loại nguyên nhân khi triển khai thật.

Điểm quyết định là **vị trí đặt `catch`**: `catch (Exception)` ở ranh giới giám sát/điều phối có thể có chủ đích, trong khi `catch (Exception)` ở mọi method cấp thấp thường che mất những lỗi không thuộc trách nhiệm của method đó.

### 9.2. Vì sao không thay hết bằng `catch (Throwable)`?

`Throwable` còn bao gồm nhánh `Error`, như `OutOfMemoryError`, `StackOverflowError`, `LinkageError` và `AssertionError`. Với nhiều lỗi nghiêm trọng, trạng thái JVM/tài nguyên có thể không cho phép tiếp tục an toàn.

`catch (Exception)` **không** bắt `Error`. `catch (Throwable)` về mặt kiểu có thể bắt, nhưng không biến JVM thành “không bao giờ sập”. Trong ứng dụng bình thường, tránh dùng `catch (Throwable)` làm handler phục hồi tổng quát.

## 10. Anti-pattern: gom mọi lỗi thành “đã xử lý thành công”

Xem đoạn minh họa:

```java
try {
    saveOrder();
} catch (Exception ignored) {
    // Không làm gì.
}
return true;
```

Đây là đoạn không độc lập; `saveOrder()` chưa được định nghĩa. Vấn đề là nếu lỗi xảy ra, caller vẫn nhận `true`. Có thể đơn hàng chưa được lưu, đã lưu một phần hoặc đã thực hiện side effect trước khi exception phát sinh.

Khi đó `catch (Exception)` tạo ảo giác thành công và **che mọi nguyên nhân**. Một multi-catch cũng có thể sai nếu xử lý như thế này. Cần thiết kế hợp đồng trước:

- Nếu có thể phục hồi: thực hiện biện pháp phục hồi có kiểm chứng, rồi trả kết quả đúng.
- Nếu không thể phục hồi: trả failure có cấu trúc, hoặc ném lại/chuyển đổi exception, giữ `cause`.
- Nếu trạng thái bên ngoài chưa xác định: dùng API xác nhận trạng thái, idempotency key hay transaction/compensation phù hợp; đừng suy luận “ném lỗi thì chưa ghi gì”.

**Chọn kiểu `catch` không tự tạo rollback.** Nó chỉ quyết định những đối tượng exception nào đi vào handler.

## 11. Thứ tự handler khi kết hợp multi-catch và kiểu cha

Có thể đặt multi-catch trước handler tổng quát:

```java
try {
    executeTask();
} catch (java.io.IOException | java.sql.SQLException e) {
    System.out.println("Lỗi nghiệp vụ hạ tầng đã dự kiến");
} catch (Exception e) {
    System.out.println("Lỗi không dự kiến");
}
```

Đoạn minh họa giả định `executeTask()` có thể ném `IOException`, `SQLException`. Đây là cấu trúc hợp lệ: handler đầu bắt hai loại được nêu, handler thứ hai bắt các `Exception` còn lại.

Nhưng thứ tự ngược lại **không hợp lệ**:

```java
// CỐ Ý SAI: handler multi-catch phía sau đã bị Exception bao phủ.
try {
    executeTask();
} catch (Exception e) {
    System.out.println("Rộng");
} catch (java.io.IOException | java.sql.SQLException e) {
    System.out.println("Cụ thể");
}
```

Java từ chối handler không thể tới được vì mọi `IOException`/`SQLException` đều là `Exception` và sẽ bị nhánh đầu giữ lại.

Nếu muốn tránh handler tổng quát, có thể bỏ `catch (Exception)` và để các lỗi không dự kiến truyền lên. Đây thường là thiết kế tốt ở tầng thư viện/domain, nơi không có đủ thông tin để sửa tất cả lỗi.

### 11.1. Một multi-catch không cho “ưu tiên” một loại ở bên trong nó

`catch (IOException | SQLException e)` có **một phần thân duy nhất**. Java không chạy một nửa của handler cho IOException và nửa kia cho SQLException. Nếu thật sự có logic khác nhau, tách `catch(IOException)` và `catch(SQLException)`; không dùng union catch để thay thế một switch trên loại exception.

## 12. Multi-catch, checked và unchecked: chọn tập lỗi đúng

Ví dụ multi-catch kết hợp checked và unchecked:

```java
try {
    parseFromFile();
} catch (java.io.IOException | NumberFormatException e) {
    System.out.println("Dữ liệu nguồn không hợp lệ");
}
```

Đoạn chỉ là minh họa, `parseFromFile()` cần thực sự có khả năng ném checked `IOException` trong chữ ký/hoạt động. Khi đó handler có thể hợp lệ vì hai kiểu không bao phủ nhau.

Tuy vậy, trước khi gộp, phải hỏi: lỗi đọc file và lỗi chuỗi số có cần cùng một hành động không? Nếu lỗi parse cần yêu cầu người dùng sửa nội dung, còn lỗi I/O cần cho chọn nguồn khác, hai handler riêng là hợp lý hơn.

**Không có quy tắc:** “checked luôn cần multi-catch”, “unchecked luôn cần catch cha” hoặc “mỗi checked exception phải có một `catch` riêng”. Java cho phép nhiều cách sắp xếp; lựa chọn phải dựa trên khả năng xử lý, hợp đồng và bảo toàn dữ liệu.

## 13. Thiết kế helper chung thay vì mở rộng handler

Nếu hai handler khác nhau ở phần đầu nhưng có bước cuối giống nhau, không bắt buộc phải dùng multi-catch. Ta có thể tách hàm chung:

```java
try {
    performOperation();
} catch (java.io.IOException e) {
    logFailure("I/O", e);
    recoverFile();
} catch (java.sql.SQLException e) {
    logFailure("SQL", e);
    haltDatabaseRequest();
}
```

Đây là đoạn minh họa phụ thuộc các method được định nghĩa ở lớp bao quanh. Hai handler có chính sách khác nhau nhưng vẫn chia sẻ `logFailure(...)`. Cách này thường rõ hơn một multi-catch chứa phân nhánh bằng `instanceof`.

Đối với API cần đóng tài nguyên, dùng try-with-resources tại nơi phù hợp. Mọi handler — multi-catch hay catch cha — đều không tự động tạo cơ chế dọn dẹp/rollback. Nếu có ngoại lệ khi đóng tài nguyên, cần hiểu primary và suppressed exceptions thay vì nuốt cả hai.

## 14. Lựa chọn theo từng tầng kiến trúc

| Tầng | Ví dụ công việc | Xu hướng phù hợp | Lý do |
| --- | --- | --- | --- |
| Parse dữ liệu đầu vào | Chuyển chuỗi thành số | `catch (NumberFormatException)` | Có thể yêu cầu nhập lại đúng trường. |
| Tầng đọc lưu trữ | Đọc file hoặc truy vấn SQL | Multi-catch nếu cùng chính sách, hoặc hai catch riêng | Không xử lý vô tình những bug thuộc `RuntimeException`. |
| Service nghiệp vụ | Tạo đơn, cập nhật tồn kho | Những exception nghiệp vụ cụ thể và hợp đồng trạng thái rõ ràng | Caller cần biết loại thất bại và mức thay đổi state. |
| CLI / request boundary | Trả lỗi người dùng, đánh dấu request thất bại | `catch (Exception)` có thể hợp lý | Là nơi có trách nhiệm gom failure thành phản hồi/trạng thái. |
| Test framework / runtime chuyên biệt | Thu thập failure của tác vụ | Handler rộng theo chính sách framework | Phải có cách ly, báo cáo, không che `Error` nghiêm trọng. |

Không nên áp dụng máy móc theo tên tầng. Một service có thể thực sự xử lý `Exception` tổng quát ở một boundary cụ thể, hoặc một request handler cần phân loại từng lỗi. Quan trọng là **có chính sách chứng minh được** cho các lỗi bị bắt.

## 15. Tình huống thực tế: vì sao `catch (Exception)` có thể phá hợp đồng?

Giả sử `saveInvoice()` hứa rằng khi không tạo được hóa đơn, caller sẽ nhận failure rõ ràng. Bên trong method vừa có thể ném `IOException`/`SQLException`, vừa có thể có bug `NullPointerException`.

Nếu viết:

```java
try {
    saveInvoice();
} catch (Exception e) {
    System.out.println("Không lưu được hóa đơn");
}
// Tiếp tục như thể hóa đơn đã tạo.
sendConfirmation();
```

Đây là đoạn minh họa, không độc lập; các method chưa được định nghĩa. Handler này bắt cả lỗi hạ tầng và bug; hơn nữa **sau catch vẫn gửi xác nhận**, làm sai hợp đồng. Thay bằng multi-catch có thể giảm phạm vi lỗi bị bắt nhưng **không tự khắc phục** vấn đề tiếp tục sau thất bại:

```java
try {
    saveInvoice();
} catch (java.io.IOException | java.sql.SQLException e) {
    throw new IllegalStateException("Không tạo được hóa đơn", e);
}
sendConfirmation();
```

Mẫu thứ hai minh họa việc **không tiếp tục đường thành công** khi lỗi hạ tầng xảy ra; wrapper giữ nguyên `cause`. Nhưng còn phải thiết kế tác động ngoài hệ thống: nếu hóa đơn đã được lưu một phần trước khi lỗi, gửi lại yêu cầu có thể tạo bản ghi trùng. Transaction, idempotency, kiểm tra trạng thái và phục hồi là những mối quan tâm bổ sung.

**Bài học:** catch chọn phạm vi lỗi; phần thân handler quyết định xử lý có đúng hợp đồng hay không. Cả hai đều cần được đánh giá.

## 16. Các lỗi biên dịch và hiểu nhầm phổ biến

| Lỗi/quan niệm | Giải thích và cách sửa |
| --- | --- |
| “Multi-catch chỉ là `catch(Exception)` viết dài hơn.” | Sai: multi-catch chỉ bao phủ các nhánh được liệt kê; không nhận toàn bộ `RuntimeException`. |
| “`catch(Exception)` chỉ bắt checked exceptions.” | Sai: `RuntimeException extends Exception`, nên handler này còn bắt nhiều unchecked exceptions. |
| “Multi-catch bắt cả `Error` nếu cùng tên thông điệp.” | Sai: chọn handler dựa trên kiểu kế thừa, không dựa vào message. |
| “Có thể viết `IOException \| FileNotFoundException`.” | Sai: hai loại có quan hệ subtype; chỉ chọn cha hoặc dùng handler riêng. |
| “Có thể viết `IOException || SQLException`.” | Sai cú pháp: multi-catch dùng `|` một dấu. |
| “Thêm `IOException` vào multi-catch là luôn hợp lệ.” | Sai: checked exception cụ thể phải phù hợp với khả năng ném từ vùng `try`. |
| “Multi-catch `e` có thể được gán lại như biến thường.” | Sai: tham số multi-catch implicitly final. |
| “`e` trong union catch dùng trực tiếp mọi method của `SQLException`.” | Sai: chỉ những thành viên hợp lệ với kiểu tĩnh chung mới gọi trực tiếp được. |
| “`catch(Exception)` rethrow luôn buộc `throws Exception`.” | Sai trong trường hợp precise rethrow áp dụng và biến catch không bị gán lại. |
| “Bắt được lỗi tức là đã rollback.” | Sai: try/catch không tự hoàn tác các side effect trước đó. |
| “Thêm `catch(Throwable)` sẽ làm chương trình không bao giờ sập.” | Sai: còn nhiều lỗi VM/process và khả năng khôi phục không bảo đảm; còn có thể nuốt `Error`. |
| “Một multi-catch luôn tốt hơn hai catch riêng.” | Sai: khi hai loại lỗi cần xử lý khác nhau, handler riêng diễn đạt đúng ý định hơn. |
| “Chỉ cần in log ở catch là lỗi đã xử lý.” | Chưa đủ nếu trạng thái thất bại không được truyền lên hoặc phục hồi không diễn ra. |
| “Trong cùng `try`, handler sau sẽ chạy tiếp sau handler đầu.” | Sai: một exception đang lan truyền được handler phù hợp đầu tiên xử lý; các nhánh cùng cấp không chạy tuần tự. |

## 17. Cách kiểm thử sự khác nhau

Một bộ kiểm thử tốt không chỉ xác nhận dòng log, mà còn xác nhận những lỗi nào **được xử lý** và lỗi nào **được phép thoát**.

Với `CatchScopeDemo`, thử:

1. Input `"io"`: multi-catch xử lý và không ném ra `main`.
2. Input `"db"`: multi-catch xử lý và không ném ra `main`.
3. Input `"bug"`: `targeted` để `IllegalStateException` thoát; caller quan sát được.
4. Input `"bug"` với `broad`: `catch (Exception)` xử lý và không ném tiếp, dù lỗi lập trình chưa chắc đã được sửa.
5. Input `"ok"`: không handler nào được thực hiện.

Có thể viết JUnit 5 để kiểm tra trực tiếp:

```java
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CatchScopeDemoTest {
    @Test
    void targetedLetsUnexpectedExceptionEscape() {
        assertThrows(IllegalStateException.class,
                () -> CatchScopeDemo.targeted("bug"));
    }

    @Test
    void broadSwallowsUnexpectedExceptionInThisDemo() {
        assertDoesNotThrow(() -> CatchScopeDemo.broad("bug"));
    }

    @Test
    void targetedHandlesExpectedCheckedExceptions() {
        assertDoesNotThrow(() -> CatchScopeDemo.targeted("io"));
        assertDoesNotThrow(() -> CatchScopeDemo.targeted("db"));
    }
}
```

**Đây là file test minh họa**, cần một dự án đã có JUnit Jupiter; không phải chương trình `javac` độc lập. `CatchScopeDemo` và test phải nằm trong package tương thích; ví dụ dùng default package. Kết quả kiểm thử “`broad` không ném ra ngoài” **không chứng minh `broad` an toàn**, chỉ xác nhận hành vi bắt rộng mà chương trình này thể hiện.

Khi test dự án thật, nên xác nhận thêm trạng thái của các đối tượng, dữ liệu đã ghi và kết quả tác vụ, đặc biệt sau exception.

## 18. Bài tập tự luyện

### Bài 1 — Dự đoán handler

Một method công bố `throws IOException, SQLException` và đôi khi ném `IllegalArgumentException`. Nếu caller dùng:

```java
try {
    call();
} catch (java.io.IOException | java.sql.SQLException e) {
    System.out.println("Đã dự kiến");
}
```

Đoạn minh họa giả định `call()` tồn tại. Với `IllegalArgumentException`, handler có chạy không? **Không.** Loại này nằm dưới `RuntimeException`, không thuộc hai nhánh checked đã liệt kê.

### Bài 2 — Sửa multi-catch sai

Vì sao `catch (IOException | FileNotFoundException e)` không biên dịch? Viết hai phiên bản sửa: (a) chỉ dùng `catch (IOException e)` cho một chính sách chung; (b) hai handler riêng, `FileNotFoundException` trước `IOException`, khi cần hành động khác nhau.

### Bài 3 — Biến `e` có thể gán lại không?

Thử thêm `e = new IOException("another");` trong `catch (IOException | SQLException e)`. Giải thích lỗi compiler. So sánh với `catch (Exception e)` thường có thể gán lại, và tại sao việc gán lại làm thay đổi phân tích precise rethrow.

### Bài 4 — Kiểu tĩnh chung của union catch

Trong `catch (IOException | SQLException e)`, gọi `e.getMessage()` hợp lệ, còn `e.getErrorCode()` không trực tiếp hợp lệ. Tại sao? Nếu thực sự cần vendor error code từ `SQLException`, thiết kế với handler riêng sẽ rõ ràng ra sao?

### Bài 5 — Rethrow chính xác

Chạy `MultiCatchRethrowDemo`. Thêm một method `forwardBroad` dùng `catch (Exception e) { throw e; }` quanh `fetch(...)`. Kiểm chứng rằng Java 17 có thể chấp nhận `throws IOException, SQLException` nhờ precise rethrow, miễn `e` không được gán lại. Sau đó thử gán lại `e` và dự đoán thông báo compiler.

### Bài 6 — Chọn chính sách xử lý

Một ứng dụng nhập dữ liệu có thể gặp: file thiếu, SQL timeout, chuỗi số không hợp lệ, `NullPointerException` do bug. Hãy xác định tầng nào thực sự xử lý từng loại, khi nào nên dùng multi-catch, khi nào dùng catch riêng, và boundary nào có thể catch rộng mà vẫn **báo failure rõ ràng**.

### Bài 7 — Đánh giá hợp đồng

Nếu một phương thức đã ghi một nửa dữ liệu rồi ném `SQLException`, liệu thay `catch (Exception)` bằng `catch (IOException | SQLException)` có tự khôi phục dữ liệu không? **Không.** Liệt kê cơ chế transaction hoặc compensation cần thiết để khôi phục trạng thái, và cách caller biết thao tác đã thất bại.

## 19. Checklist chọn handler trước khi commit

- [ ] Tôi có biết chính xác **những loại ngoại lệ mà tầng này xử lý được** không?
- [ ] Các loại đó có cùng một hành động xử lý không? Nếu không, nên tách thành handler riêng.
- [ ] Các lựa chọn của multi-catch có quan hệ cha–con gây lỗi biên dịch không?
- [ ] Mọi lựa chọn checked trong multi-catch có thực sự có nguồn khả năng ném từ `try` không?
- [ ] Handler có vô tình bắt `RuntimeException` không dự kiến và che bug không?
- [ ] Tôi có cần bắt `Exception` ở boundary giám sát thay vì ở tầng nghiệp vụ thấp không?
- [ ] Nếu bắt rộng, liệu kết quả có được đánh dấu thất bại thay vì giả thành công không?
- [ ] Nếu dùng `throw e;`, có giữ nguyên đối tượng và cause cần chẩn đoán không?
- [ ] Tôi có phân biệt precise rethrow với phạm vi handler runtime không?
- [ ] Biến multi-catch `e` có được giữ nguyên theo quy tắc implicitly final không?
- [ ] Các handler đã theo thứ tự hợp lệ, không có catch unreachable chưa?
- [ ] Tài nguyên và trạng thái khi xảy ra exception có được dọn dẹp/rollback theo cơ chế thật, không phải theo giả định không?
- [ ] Kiểm thử có bao gồm cả checked exception dự kiến và unchecked exception ngoài kế hoạch chưa?

**Kết luận:** **Multi-catch gom các ngoại lệ được chỉ đích danh khi chúng cần cùng một cách xử lý; `catch (Exception)` nhận toàn bộ nhánh `Exception`, gồm cả những lỗi lập trình chưa được dự kiến.** Ở tầng ứng dụng có khả năng xử lý cụ thể, ưu tiên handler đúng phạm vi; ở boundary giám sát, bắt rộng có thể phù hợp nếu luôn báo thất bại và bảo toàn chẩn đoán. Cả hai lựa chọn không thay cho thiết kế hợp đồng trạng thái, dọn dẹp tài nguyên và kiểm thử đường lỗi.

</details>

- [Quay lại đầu trang](#back-to-top)
