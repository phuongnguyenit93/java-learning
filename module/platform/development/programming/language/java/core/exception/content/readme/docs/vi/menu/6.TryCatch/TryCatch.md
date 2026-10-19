<a id="back-to-top"></a>

# Try và catch

## Menu
- [Cú pháp try-catch và phạm vi được bảo vệ](#try-catch-001)
- [Bắt ngoại lệ theo kiểu đối tượng thực tế](#try-catch-002)
- [Nhiều khối catch và thứ tự từ cụ thể đến tổng quát](#try-catch-003)
- [Khối catch đầu tiên phù hợp được thực thi](#try-catch-004)
- [Phạm vi của biến trong try và tham số catch](#try-catch-005)
- [Biến cục bộ và yêu cầu được gán giá trị chắc chắn](#try-catch-006)
- [Ngoại lệ phát sinh ngay trong khối catch](#try-catch-007)
- [Try-catch lồng nhau và ranh giới xử lý](#try-catch-008)
- [Catch không thể tới do khối catch trước đã bao phủ](#try-catch-009)
- [Quy tắc bắt checked exception mà khối try không thể ném](#try-catch-010)

## <a id="try-catch-001">Cú pháp try-catch và phạm vi được bảo vệ</a>

<details>
<summary>Xem chi tiết</summary>

# Cú pháp `try-catch` và phạm vi được bảo vệ trong Java

`try-catch` là cấu trúc kiểm soát luồng dùng để **xử lý ngoại lệ phát sinh khi chạy một đoạn mã**. Khối `try` xác định các thao tác được bảo vệ; các nhánh `catch` xác định **loại ngoại lệ nào được tiếp nhận** và **phải làm gì sau khi thao tác thất bại**. Hiểu chính xác phạm vi này quan trọng hơn việc chỉ nhớ cú pháp: đặt một lời gọi ở ngoài `try` thì handler tương ứng có thể không bảo vệ được lời gọi đó; gom quá nhiều thao tác không liên quan trong một `try` cũng dễ bắt nhầm lỗi.

Một nhận định cơ bản:

> `catch` đi kèm một `try` chỉ xem xét ngoại lệ **thoát ra trong lúc thực thi phần thân `try` đó** (bao gồm các phương thức được gọi từ trong khối). Nó không tự động bảo vệ mọi câu lệnh trong phương thức, những câu lệnh nằm bên ngoài `try`, hoặc công việc chạy trên một thread khác.

## 1. Cú pháp đầy đủ và các thành phần

Cấu trúc phổ biến:

```java
try {
    // Những thao tác cần được bảo vệ.
} catch (IllegalArgumentException ex) {
    // Xử lý ngoại lệ phù hợp với IllegalArgumentException.
} catch (IllegalStateException ex) {
    // Xử lý ngoại lệ phù hợp với IllegalStateException.
} finally {
    // Dọn dẹp/kết thúc theo quy tắc của finally.
}
```

Đây là **cấu trúc minh họa**, chưa có thao tác thực tế trong `try`. Ý nghĩa:

- **`try`:** phần thân được thực thi theo thứ tự bình thường cho tới khi hoàn thành hoặc có ngoại lệ làm dừng đường thực thi ấy.
- **`catch (ExceptionType variable)`:** nhánh được xét khi ngoại lệ thoát ra từ `try`. `ExceptionType` phải là `Throwable` hoặc một lớp con; tên `variable` là tham chiếu tới đối tượng lỗi đã bắt.
- **`finally` (tùy chọn):** một phần xử lý kết thúc hoặc dọn dẹp, thường chạy sau `try`/`catch` dù thành công, thất bại hay có `return`. Có những trường hợp JVM kết thúc bất thường khiến `finally` không được bảo đảm thực thi.
- **Câu lệnh phía sau cấu trúc:** được thực thi nếu toàn bộ cấu trúc `try-catch-finally` hoàn tất bình thường; không tự động chạy nếu ngoại lệ vẫn chưa được xử lý.

Với `try` thông thường, phải có **ít nhất một `catch` hoặc `finally`**. Các biến thể hợp lệ:

```java
try {
    operation();
} catch (RuntimeException ex) {
    handle(ex);
}
```

```java
try {
    operation();
} finally {
    cleanup();
}
```

```java
try {
    operation();
} catch (RuntimeException ex) {
    handle(ex);
} finally {
    cleanup();
}
```

Các đoạn này là **khung minh họa**, giả định `operation`, `handle`, `cleanup` được định nghĩa. Trường hợp `try-finally` không có `catch` **không tự bắt ngoại lệ**; `finally` chạy theo quy tắc rồi lỗi tiếp tục lan ra nếu chưa được xử lý.

`try-with-resources` là một dạng `try` đặc biệt dùng cho tài nguyên `AutoCloseable`, có thể viết không có `catch` và `finally` riêng vì bản thân cú pháp quản lý việc đóng tài nguyên. Phần cuối bài có ví dụ để phân biệt.

## 2. Phạm vi được bảo vệ là phần thân `try` và những lời gọi thực thi từ đó

Xem đoạn mã:

```java
prepare();
try {
    parse();
    save();
} catch (IllegalArgumentException ex) {
    report(ex);
}
finish();
```

Đây là đoạn **giả mã Java minh họa**: các phương thức cần được định nghĩa, và mã phải nằm trong một phương thức/lớp thực tế.

- `prepare()` đứng **trước `try`**: ngoại lệ nó ném không đi vào `catch` đang xét.
- `parse()` và `save()` được gọi **bên trong `try`**: nếu chúng hoặc các hàm con của chúng ném `IllegalArgumentException` chưa được bắt ở tầng trong, handler ở đây có thể nhận lỗi.
- `report(ex)` chạy **bên trong nhánh `catch`**, không nằm trong thân `try` vừa nêu. Nếu chính `report` ném ngoại lệ, các nhánh `catch` còn lại của cùng cấu trúc không tự bắt lỗi mới đó.
- `finish()` đứng **sau cấu trúc `try-catch`**: ngoại lệ tại đây không thuộc phạm vi bảo vệ của `try` phía trên.
- Nếu `parse()` ném ngoại lệ được bắt, `save()` sẽ bị bỏ qua ở lần thực thi này. Java chuyển tới handler, không quay lại vị trí sau `parse()` để tiếp tục `save()`.

“Phạm vi bảo vệ” có hai mặt:

1. **Phạm vi câu lệnh:** phần thân `try` chứa những lệnh nào?
2. **Phạm vi chuỗi gọi động:** khi một lệnh trong `try` gọi một phương thức khác, ngoại lệ chưa được xử lý ở sâu trong phương thức đó có thoát về điểm gọi trong `try` hay không?

Không cần đặt `try-catch` vào từng phương thức trong chuỗi A → B → C nếu tầng A có trách nhiệm xử lý lỗi từ C. Chỉ cần lời gọi B nằm trong `try` của A và lỗi đi qua B mà không bị bắt ở dưới, `catch` của A có thể xử lý.

### Phân biệt phạm vi của `try` và phạm vi của biến

Biến cục bộ được khai báo **bên trong phần thân `try`** không thể được tham chiếu trực tiếp từ `catch` hoặc câu lệnh sau `try`:

```java
try {
    int result = Integer.parseInt("42");
    System.out.println(result);
} catch (NumberFormatException ex) {
    // Không thể sử dụng biến result ở đây.
}
```

Ngược lại, một biến được khai báo ngoài `try` vẫn chịu quy tắc **definite assignment** trước khi đọc. Ví dụ sau là hợp lệ:

```java
int result;
try {
    result = Integer.parseInt("42");
} catch (NumberFormatException ex) {
    result = 0;
}
System.out.println(result);
```

Vì cả đường thành công và đường xử lý lỗi đều gán `result`, Java chứng minh biến đã được gán trước khi in. Điều này không có nghĩa **luôn** nên trả `0` khi parse thất bại: phải xác định rõ `0` có phải giá trị dự phòng hợp lệ theo hợp đồng hay không. Nếu không, ném lại hoặc chuyển lỗi thành kết quả tường minh sẽ đúng hơn.

## 3. Ba đường đi chính của một cấu trúc `try-catch`

### Trường hợp A — không có ngoại lệ

```text
Vào try
  → chạy các câu lệnh trong try
  → không chạy catch
  → chạy finally nếu có
  → tiếp tục sau try-catch-finally
```

### Trường hợp B — ngoại lệ phù hợp được ném trong `try`

```text
Vào try
  → chạy đến câu lệnh gây lỗi
  → bỏ qua phần còn lại của try
  → tìm catch phù hợp đầu tiên
  → chạy catch được chọn
  → chạy finally nếu có
  → tiếp tục sau cấu trúc nếu catch hoàn tất bình thường
```

Một ngoại lệ cụ thể chỉ được xử lý bởi **một handler được chọn** của cùng cấu trúc `try`; Java không chạy tuần tự mọi `catch` phù hợp.

### Trường hợp C — không có `catch` nào phù hợp

```text
Vào try
  → phát sinh ngoại lệ
  → không có catch nào phù hợp
  → chạy finally nếu có
  → ngoại lệ tiếp tục lan ra caller hoặc try ở ngoài
```

Lưu ý: kết quả còn phụ thuộc `finally`, bởi `finally` có thể ném ngoại lệ khác hoặc dùng `return`; một thiết kế tốt nên tránh làm mất lỗi gốc bằng các thao tác thay đổi luồng trong `finally`.

## 4. Ví dụ Java 17+ hoàn chỉnh: xác định câu lệnh nào chạy

Lưu toàn bộ đoạn dưới đây vào tệp **`TryCatchScopeDemo.java`**:

```java
public class TryCatchScopeDemo {

    static int parsePositive(String raw) {
        int number = Integer.parseInt(raw);
        if (number <= 0) {
            throw new IllegalArgumentException(
                    "number must be positive");
        }
        return number;
    }

    static void handle(String raw) {
        System.out.println("CASE " + raw);
        System.out.println("outside-before: ready");

        try {
            System.out.println("inside: begin");
            int result = parsePositive(raw);
            System.out.println("inside: result=" + result);
        } catch (NumberFormatException ex) {
            System.out.println("catch-format: invalid integer");
        } catch (IllegalArgumentException ex) {
            System.out.println("catch-rule: " + ex.getMessage());
        } finally {
            System.out.println("finally: done");
        }

        System.out.println("outside-after: continue");
    }

    static void nested() {
        System.out.println("NESTED");

        try {
            try {
                System.out.println("inner: begin");
                throw new IllegalStateException("inner failed");
            } catch (NumberFormatException ex) {
                System.out.println("inner-catch");
            } finally {
                System.out.println("inner-finally");
            }
        } catch (IllegalStateException ex) {
            System.out.println("outer-catch: " + ex.getMessage());
        }

        System.out.println("nested: continue");
    }

    public static void main(String[] args) {
        handle("3");
        handle("0");
        handle("abc");
        nested();
    }
}
```

Lệnh biên dịch/chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 TryCatchScopeDemo.java
java TryCatchScopeDemo
```

**Stdout chính xác:**

```text
CASE 3
outside-before: ready
inside: begin
inside: result=3
finally: done
outside-after: continue
CASE 0
outside-before: ready
inside: begin
catch-rule: number must be positive
finally: done
outside-after: continue
CASE abc
outside-before: ready
inside: begin
catch-format: invalid integer
finally: done
outside-after: continue
NESTED
inner: begin
inner-finally
outer-catch: inner failed
nested: continue
```

### Đọc từng nhánh

**`handle("3")`:** `parsePositive` trả về `3`. Toàn bộ phần `try` chạy, không có `catch` nào được chọn; `finally` vẫn chạy. Sau đó phương thức thực hiện câu lệnh `outside-after`.

**`handle("0")`:** parse thành công, nhưng số không dương nên `parsePositive` ném `IllegalArgumentException`. Chương trình **không in** dòng `inside: result=...`. Handler `NumberFormatException` không phù hợp, handler `IllegalArgumentException` được chọn, rồi `finally` và phần sau cấu trúc chạy.

**`handle("abc")`:** `Integer.parseInt` ném `NumberFormatException`. Vì lớp này kế thừa `IllegalArgumentException`, cả hai handler có quan hệ kiểu phù hợp, nhưng Java chọn **nhánh đầu tiên** `catch (NumberFormatException)`. Nhánh `catch (IllegalArgumentException)` không chạy thêm lần nữa.

**`nested()`:** ngoại lệ `IllegalStateException` phát sinh trong `try` bên trong. `catch (NumberFormatException)` bên trong không phù hợp; `finally` bên trong chạy. Ngoại lệ tiếp tục lan tới handler `IllegalStateException` của `try` bên ngoài, rồi phương thức tiếp tục bình thường.

Từ chương trình này có thể thấy **phạm vi đang bảo vệ thực sự dựa vào chỗ đặt câu lệnh** và **quan hệ khớp kiểu**. Khi xử lý xong một ngoại lệ, JVM không chạy tiếp những lệnh trong `try` đã bị bỏ qua.

## 5. Java chọn nhánh `catch` nào?

Đối với cùng một cấu trúc `try`, các nhánh `catch` được xem xét **theo thứ tự viết từ trên xuống**. Một handler khớp khi đối tượng ngoại lệ thuộc kiểu được khai báo hoặc một **lớp con** của nó.

Ví dụ:

```java
try {
    Integer.parseInt("x");
} catch (NumberFormatException ex) {
    System.out.println("number format");
} catch (IllegalArgumentException ex) {
    System.out.println("other illegal argument");
}
```

Kết quả:

```text
number format
```

`NumberFormatException extends IllegalArgumentException` nên phải đặt `catch` cụ thể trước `catch` tổng quát. Nếu đảo thứ tự:

```java
// Cố ý không biên dịch:
try {
    Integer.parseInt("x");
} catch (IllegalArgumentException ex) {
    System.out.println("general");
} catch (NumberFormatException ex) {
    System.out.println("specific");
}
```

Trình biên dịch báo nhánh `NumberFormatException` không thể tới được, bởi handler `IllegalArgumentException` phía trước đã bao phủ mọi đối tượng thuộc subtype đó.

Những ghi nhớ cần thiết:

- `catch (Exception ex)` bắt những ngoại lệ thuộc `Exception`, gồm `RuntimeException`, nhưng **không bắt `Error`**.
- `catch (RuntimeException ex)` không bắt checked `IOException`.
- `catch (IOException ex)` có thể bắt `FileNotFoundException`, nhưng không bắt `IllegalArgumentException`.
- `catch (Throwable ex)` bắt cả `Error`, song thường không thích hợp cho xử lý lỗi nghiệp vụ.
- Handler dành riêng cho nhiều nhánh có phản ứng khác nhau giúp mã dễ bảo trì; không cần bắt rộng rồi kiểm tra chuỗi message để phân loại.

### `catch` dành cho checked exception phải có cơ sở

Một đoạn như sau **cố ý không biên dịch**:

```java
try {
    System.out.println("hello");
} catch (java.io.IOException ex) {
    System.out.println("I/O failed");
}
```

Phần `try` không thể phát sinh checked `IOException` theo phân tích của trình biên dịch, nên handler ấy bị coi là không thể tới được. Đừng thêm `catch (IOException)` một cách máy móc chỉ vì đang viết mã Java; hãy đặt nó quanh lời gọi thực sự có thể ném loại checked exception này.

Ngoại lệ unchecked có quy tắc kiểm tra khác: trình biên dịch không buộc mọi đường ném `RuntimeException` phải được khai báo trước.

## 6. Ngoại lệ trong `catch` có được các `catch` “anh em” bắt không?

**Không.** Những nhánh `catch` xuất hiện sau không phải mạng lưới bảo vệ phần thân của nhau. Ví dụ:

```java
try {
    throw new NumberFormatException("bad number");
} catch (NumberFormatException ex) {
    throw new IllegalStateException("handler failed", ex);
} catch (IllegalStateException ex) {
    System.out.println("This line is not reached");
}
```

Đây là **đoạn minh họa**, cần đặt trong phương thức hợp lệ. Nó có thể biên dịch, nhưng nhánh `catch (IllegalStateException)` phía sau **không bắt** lỗi mới ném từ handler trước. `IllegalStateException` lan ra vùng xử lý bên ngoài; nếu không có handler ngoài, nó có thể trở thành ngoại lệ chưa được bắt.

Nếu mục tiêu là bắt luôn lỗi phát sinh từ handler đầu tiên, cần một cấu trúc `try-catch` **bao ngoài**, hoặc thiết kế lại handler cho an toàn.

Mô hình đúng:

```java
try {
    try {
        throw new NumberFormatException("bad number");
    } catch (NumberFormatException ex) {
        throw new IllegalStateException("converted", ex);
    }
} catch (IllegalStateException ex) {
    System.out.println("outer catch: " + ex.getMessage());
}
```

Đầu ra:

```text
outer catch: converted
```

Ở đây `catch` ngoài bảo vệ **toàn bộ cấu trúc `try-catch` trong**, nên nó nhận được ngoại lệ do handler trong ném. Đây cũng là lý do một `try-catch` nằm trong phương thức gọi có thể xử lý lỗi từ phương thức con dù con đã bắt một lỗi rồi ném một loại lỗi mới.

## 7. `try-catch` bảo vệ được những lời gọi sâu đến đâu?

Giả sử:

```java
static void stepC() {
    throw new IllegalStateException("error at C");
}

static void stepB() {
    stepC();
}

static void stepA() {
    try {
        stepB();
    } catch (IllegalStateException ex) {
        System.out.println("A handles: " + ex.getMessage());
    }
}
```

Đây là **ba phương thức minh họa**, cần đặt trong cùng một lớp. Mặc dù `throw` xảy ra ở `stepC`, handler `catch` của `stepA` vẫn bắt được vì:

1. `stepA` gọi `stepB` **khi đang ở trong `try`**.
2. `stepB` gọi `stepC`, và `stepC` ném lỗi.
3. Không có handler phù hợp trong `stepC` hay `stepB`, nên ngoại lệ quay lên điểm gọi `stepB()` thuộc `try` của `stepA`.
4. `catch (IllegalStateException)` của A phù hợp và được chạy.

Nếu thay `stepB()` bằng một thao tác bất đồng bộ khởi chạy thread riêng, quy tắc này **không còn tương đương**: `try-catch` quanh lệnh `thread.start()` không tự động bắt ngoại lệ phát sinh sau đó trong thread mới. Với tác vụ submit tới `ExecutorService`, lỗi có thể được quan sát qua `Future.get()`/`ExecutionException` tùy API.

## 8. `try-catch` lồng nhau: handler gần phạm vi thực thi hơn được xét trước

Xét:

```java
try {
    try {
        throw new NumberFormatException("abc");
    } catch (IllegalArgumentException ex) {
        System.out.println("inner handles");
    }
} catch (NumberFormatException ex) {
    System.out.println("outer handles");
}
```

Đầu ra:

```text
inner handles
```

Handler **bên trong** đã bắt `NumberFormatException` qua kiểu cha `IllegalArgumentException`. Handler bên ngoài dù khai báo kiểu con cụ thể hơn **không nhận lỗi** vì ngoại lệ đã được xử lý.

Nếu bên trong không có `catch` khớp, ngoại lệ mới lan ra ngoài. JVM không chọn “handler có kiểu cụ thể nhất trên toàn bộ mã nguồn”; nó tìm handler phù hợp trong các vùng đang có hiệu lực, xét từ vùng trong ra vùng ngoài.

Bài học thiết kế: một `catch (Exception ex)` đặt quá thấp và nuốt lỗi có thể khiến tầng gọi bên ngoài không bao giờ nhận được ngoại lệ nghiệp vụ cụ thể. Đừng bắt lỗi ở tầng không có hành động xử lý có ý nghĩa.

## 9. `finally` có nằm trong phạm vi `catch` không?

Một `finally` gắn với `try-catch` thường thực thi khi rời cấu trúc, kể cả khi có `return` hoặc ngoại lệ cần lan truyền. Nhưng **ngoại lệ phát sinh từ `finally` không được những `catch` anh em của cùng cấu trúc bắt**.

Ví dụ:

```java
try {
    System.out.println("try success");
} catch (IllegalArgumentException ex) {
    System.out.println("catch argument");
} finally {
    throw new IllegalStateException("cleanup failed");
}
```

Đoạn minh họa trên in `"try success"` rồi ném `IllegalStateException`. `catch (IllegalArgumentException)` không xử lý ngoại lệ phát sinh sau đó trong `finally`; người gọi/`try` bên ngoài phải xử lý nếu cần.

Đặc biệt, `return` hoặc `throw` trong `finally` có thể **thay đổi đường hoàn tất** và che mất lỗi đang lan truyền từ `try`/`catch`. Do đó nên dùng `finally` cho dọn dẹp có kiểm soát; không lạm dụng `return` trong `finally`.

### Quản lý tài nguyên: ưu tiên `try-with-resources`

Với đối tượng triển khai `AutoCloseable`, dùng:

```java
static String firstLine(java.nio.file.Path path)
        throws java.io.IOException {
    try (var reader = java.nio.file.Files.newBufferedReader(path)) {
        return reader.readLine();
    }
}
```

Đây là **một phương thức minh họa**, cần một `Path` hợp lệ khi gọi và có thể ném `IOException`. Reader sẽ được đóng khi rời phần thân `try`, kể cả khi việc đọc gặp ngoại lệ. Nếu thao tác chính và thao tác đóng cùng lỗi, Java có cơ chế **suppressed exceptions** giúp giữ lỗi đóng cùng ngoại lệ chính.

Các lưu ý:

- `try-with-resources` không bảo đảm tài nguyên ngoài process (chẳng hạn giao dịch cơ sở dữ liệu) tự rollback trong mọi trường hợp; điều đó phụ thuộc API và giao dịch.
- Nếu tài nguyên `close()` ném ngoại lệ, loại checked exception của việc đóng cũng có thể cần được bắt/khai báo theo hợp đồng.
- Không phải tài nguyên nào cũng cho phép tái sử dụng sau khi đóng.
- Có thể kết hợp `try-with-resources` với `catch` và `finally` để xử lý lỗi và hoàn tất công việc.

## 10. Multi-catch và phạm vi một handler

Khi nhiều kiểu ngoại lệ **không có quan hệ cha/con** được xử lý theo cách giống nhau, có thể viết:

```java
try {
    operation();
} catch (java.io.IOException | NumberFormatException ex) {
    System.out.println("Operation failed: "
            + ex.getClass().getSimpleName());
}
```

Đây là một cấu trúc minh họa; `operation()` cần là phương thức có khả năng ném checked `IOException` nếu dùng đoạn này như mã hoàn chỉnh. Biến `ex` của multi-catch được xem là **ngầm `final`**, không được gán lại. Các kiểu lựa chọn phải không nằm trong quan hệ kế thừa trực tiếp hoặc gián tiếp.

Không hợp lệ:

```java
// Cố ý không biên dịch: NumberFormatException là subclass.
catch (NumberFormatException | IllegalArgumentException ex) {
    // ...
}
```

Dùng `catch (IllegalArgumentException)` nếu hành động xử lý là như nhau; nếu khác nhau, dùng các `catch` riêng với subclass trước. `multi-catch` không làm rộng phạm vi các câu lệnh được bảo vệ: nó vẫn chỉ thuộc về **phần `try` tương ứng**.

## 11. Đặt `try` hẹp hay rộng?

### `try` quá rộng

```java
try {
    parseInput();
    updateAccount();
    sendNotification();
} catch (IllegalArgumentException ex) {
    showInputError();
}
```

Các phương thức là **tên minh họa**. Nếu `updateAccount` cũng ném `IllegalArgumentException` do một sai lệch lập trình hoặc trạng thái, handler `showInputError` có thể báo nhầm thành “người dùng nhập sai”. Một handler xử lý một loại lỗi nhưng phần `try` chứa nhiều thao tác không liên quan làm **nguồn lỗi không rõ**.

### `try` quá hẹp

```java
try {
    prepareInput();
} catch (IllegalArgumentException ex) {
    showInputError();
}
parseInput();
```

Nếu lỗi cần xử lý lại phát sinh từ `parseInput()` **nằm ngoài `try`**, handler phía trên không giúp gì. Chưa kể nếu `parseInput` có checked exception, người viết vẫn phải tuân theo hợp đồng `throws` tương ứng.

### Phạm vi có trách nhiệm rõ ràng

```java
final Input input;
try {
    input = parseInput();
} catch (InvalidInputException ex) {
    showInputError(ex);
    return;
}
updateAccount(input);
```

Đoạn trên là **mô hình minh họa**, cần các kiểu `Input`, `InvalidInputException` và phương thức tương ứng. Ý tưởng là chỉ bắt lỗi thuộc bước parse tại ranh giới hiểu được lỗi nhập liệu. Nếu `updateAccount` thất bại vì sự cố hệ thống, hãy để xử lý tại nơi khác phù hợp hơn, chẳng hạn handler của tầng ứng dụng.

Không cần cố ép `try` thành một dòng. **Phạm vi đúng là phạm vi của thao tác và chính sách xử lý**: một giao dịch nhiều bước có thể cần vùng `try` bao cả giao dịch để quản lý rollback ở ranh giới chung. Quan trọng là biết handler đang nhận được những loại lỗi từ thao tác nào.

## 12. Phân biệt `try-catch` với `throws`

`try-catch` xử lý ngoại lệ **khi thực thi**; `throws` khai báo rằng phương thức có thể để một ngoại lệ **lan ra**. Hai cấu trúc phục vụ mục đích khác nhau.

Ví dụ:

```java
static String readConfig(java.nio.file.Path file)
        throws java.io.IOException {
    return java.nio.file.Files.readString(file);
}

static void start(java.nio.file.Path file) {
    try {
        String value = readConfig(file);
        System.out.println(value);
    } catch (java.io.IOException ex) {
        System.out.println("Configuration unavailable");
    }
}
```

Đây là hai phương thức minh họa. `readConfig` khai báo `throws IOException` và không tự bắt lỗi. `start` bao quanh **lời gọi `readConfig`** bằng `try` và bắt lỗi I/O. Phương thức `start` có thể chọn cách khác là khai báo `throws IOException` để chuyển trách nhiệm cho tầng bên ngoài.

Khai báo `throws IOException` **không** tự chặn việc thực thi hoặc xử lý lỗi; còn `catch (IOException)` phải nằm trong vùng `try` bảo vệ lời gọi có nguy cơ, và được xét chỉ khi ngoại lệ tương ứng thực sự thoát ra.

Nếu `readConfig` ném unchecked exception như `NullPointerException` do lỗi lập trình, `catch (IOException)` không bắt nó. Chọn phạm vi và loại handler theo lỗi mà tầng này **có trách nhiệm xử lý**, không phải theo suy nghĩ rằng đã có `catch` thì không thể xảy ra lỗi nữa.

## 13. Những lỗi phổ biến về cú pháp và phạm vi

| Lỗi | Vì sao sai | Cách xử lý |
| --- | --- | --- |
| Viết `try { ... }` bình thường nhưng không có `catch` hay `finally` | Cú pháp `try` thường cần ít nhất một trong hai | Thêm `catch`/`finally`, hoặc dùng `try-with-resources` đúng dạng |
| Đặt lời gọi dễ lỗi ở ngoài `try` | Handler không có hiệu lực cho lời gọi ấy | Bao đúng thao tác cần xử lý |
| Đặt `catch (Exception)` trước `catch (IOException)` | Handler lớp con bị che khuất | Đưa `IOException` lên trước hoặc bỏ nhánh dư |
| Mong handler thứ hai bắt lỗi ném ra từ handler thứ nhất | Các `catch` cùng cấp không bảo vệ nhau | Dùng một `try-catch` bên ngoài hoặc thay logic |
| Nghĩ `catch` được chạy cả khi không có ngoại lệ | `catch` chỉ chạy khi có lỗi phù hợp | Đặt logic luôn cần chạy ngoài `catch` hoặc trong `finally` |
| Nghĩ `finally` tự động nuốt ngoại lệ | Thường ngoại lệ tiếp tục lan sau `finally` | Có handler phù hợp tại ranh giới có thể xử lý |
| Dùng biến khai báo bên trong `try` từ `catch` | Phạm vi biến chỉ nằm trong block khai báo | Khai báo ngoài khi thực sự cần, xử lý definite assignment |
| Viết `catch(IOException)` khi `try` không thể ném lỗi checked đó | Handler có thể bị compiler báo không thể tới được | Bắt đúng kiểu ngoại lệ của thao tác được gọi |
| Bao toàn bộ phương thức trong `try` rồi `catch(Exception) { return null; }` | Che lấp lỗi, không rõ thao tác thất bại | Thu hẹp phạm vi và giữ lỗi theo hợp đồng |
| Dùng `try-catch` quanh `thread.start()` để bắt lỗi tác vụ sau đó | Tác vụ chạy trên stack của thread khác | Dùng kết quả bất đồng bộ/handler của tác vụ, tùy API |
| Gọi `return` trong `finally` | Có thể che mất kết quả hoặc ngoại lệ đang chờ | Tránh `return`/`throw` không cần thiết trong `finally` |

## 14. Tự luyện đọc luồng thực thi

**Bài A — ngoại lệ trong `try` và handler phù hợp**

```java
try {
    System.out.println("A");
    Integer.parseInt("abc");
    System.out.println("B");
} catch (NumberFormatException ex) {
    System.out.println("C");
}
System.out.println("D");
```

Kết quả:

```text
A
C
D
```

Dòng B bị bỏ qua vì ngoại lệ đã làm gián đoạn phần còn lại của `try`.

**Bài B — ngoại lệ phát sinh ngoài `try`**

```java
Integer.parseInt("abc");
try {
    System.out.println("inside");
} catch (NumberFormatException ex) {
    System.out.println("caught");
}
```

Không có handler phía ngoài, ngoại lệ xảy ra ngay ở lệnh đầu và những câu sau không được thực thi trên cùng đường gọi. `catch` của khối phía dưới **không bảo vệ câu lệnh trước nó**.

**Bài C — ngoại lệ ném từ handler**

```java
try {
    throw new IllegalArgumentException("first");
} catch (IllegalArgumentException ex) {
    throw new IllegalStateException("second", ex);
} catch (IllegalStateException ex) {
    System.out.println("sibling");
}
```

Không in `sibling`. Lỗi `IllegalStateException` ném từ handler thứ nhất sẽ truyền ra ngoài; handler thứ hai chỉ áp dụng cho lỗi từ phần `try`.

**Bài D — handler bên trong kiểu cha**

```java
try {
    try {
        throw new NumberFormatException("bad");
    } catch (IllegalArgumentException ex) {
        System.out.println("inner");
    }
} catch (NumberFormatException ex) {
    System.out.println("outer");
}
```

Kết quả là `inner`. Ngoại lệ được bắt ở phạm vi bên trong; tầng ngoài không nhận lại nếu không có `throw` mới.

**Bài E — `finally` khi có lỗi không được bắt ở tầng hiện tại**

```java
try {
    throw new IllegalStateException("failure");
} catch (NumberFormatException ex) {
    System.out.println("number format");
} finally {
    System.out.println("cleanup");
}
```

Cấu trúc trên in `cleanup` rồi `IllegalStateException` tiếp tục lan ra caller (hoặc thành uncaught exception nếu không có handler phù hợp). Dòng `number format` không xuất hiện.

## 15. Checklist trước khi thêm `try-catch`

1. **Đoạn mã nào thực sự cần được bảo vệ?** Xác định đúng lời gọi hoặc thao tác có khả năng phát sinh ngoại lệ bạn muốn xử lý.
2. **Ngoại lệ đi từ đâu tới?** Xem API/thân phương thức được gọi; nếu lỗi đã được handler bên trong xử lý, nó không còn lan lên handler ngoài.
3. **Chọn đúng kiểu `catch` chưa?** Dựa trên kiểu thực tế, quan hệ lớp cha/con và chính sách xử lý; đặt subtype trước supertype.
4. **Handler có khả năng làm gì?** Phục hồi, chuyển trạng thái thất bại, hiển thị lỗi phù hợp hoặc ném lại có ngữ cảnh. Không nuốt lỗi chỉ để chương trình “chạy tiếp”.
5. **Phần còn lại của `try` có bị bỏ qua không?** Nếu một lời gọi ném ngoại lệ, các câu sau trên đường thành công không chạy. Đừng đặt giả định rằng mọi tác động đã xảy ra đều tự hoàn tác.
6. **Có tài nguyên cần đóng không?** Dùng `try-with-resources` cho `AutoCloseable`; tránh che nguyên nhân bằng lỗi hoặc `return` trong `finally`.
7. **Phạm vi có rộng quá không?** Nếu một handler có thể bắt lỗi từ nhiều thao tác khác bản chất, cân nhắc tách ranh giới để thông báo và phục hồi chính xác.
8. **Có thread/tác vụ bất đồng bộ không?** `try-catch` chỉ xử lý ngoại lệ trên đường thực thi động thuộc phạm vi của nó, không tự lấy lỗi ở thread khác.

**Ghi nhớ:** khối `try` xác định **phần mã đang được bảo vệ**; các `catch` xác định **kiểu lỗi và hành động xử lý**; `finally` dành cho **công việc kết thúc**. Java không chạy lại câu lệnh đã thất bại sau khi `catch` xong, không cho `catch` ở dưới “bảo vệ ngược” câu lệnh ở trên, và không tự bắt ngoại lệ từ thread khác. Đặt phạm vi hợp lý giúp xử lý lỗi đúng nguồn và tránh giấu lỗi quan trọng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="try-catch-002">Bắt ngoại lệ theo kiểu đối tượng thực tế</a>

<details>
<summary>Xem chi tiết</summary>

# Bắt ngoại lệ theo kiểu đối tượng thực tế — `try` và `catch` trong Java

> **Ngữ cảnh:** Java Core → Exception → Try và catch. Khi một ngoại lệ được ném, Java chọn `catch` dựa trên **kiểu thực tế (runtime class) của đối tượng `Throwable` đang lan truyền** và quan hệ kế thừa của kiểu đó. Kiểu của biến tham chiếu tại nơi `throw`, tên kiểu trong `throws` và lớp nằm trong `getCause()` có vai trò riêng; chúng **không thay thế** kiểu runtime của đối tượng được ném khi quyết định nhánh `catch`.

## 1. Kết luận cần nhớ

Cho hai lớp:

```text
Throwable
└── Exception
    └── RuntimeException
        └── IllegalArgumentException
            └── NumberFormatException
```

Với đối tượng `new NumberFormatException(...)`:

- `catch (NumberFormatException e)` nhận được nó.
- `catch (IllegalArgumentException e)` cũng có thể nhận được nó vì là kiểu cha.
- `catch (RuntimeException e)`, `catch (Exception e)` và `catch (Throwable e)` cũng tương thích.
- `catch (IOException e)` không tương thích vì nhánh kế thừa khác.
- Nếu nhiều nhánh `catch` nối tiếp cùng một `try`, **nhánh tương thích đầu tiên theo thứ tự mã nguồn** được chọn. Vì Java kiểm tra các nhánh không thể tới được, nhánh cho lớp con phải đặt trước lớp cha bao trùm nó.

Phân biệt ngay hai thời điểm:

1. **Compile-time:** trình biên dịch kiểm tra cú pháp, kiểu hợp lệ của tham số `catch`, thứ tự handler và nghĩa vụ với checked exception.
2. **Runtime:** khi một đối tượng thực sự bị ném, JVM xét loại thực tế của đối tượng để tìm handler tương thích đang bao quanh đường thực thi.

Một biến có kiểu khai báo `Exception` hoàn toàn có thể trỏ đến `NumberFormatException`. Điều quan trọng với `catch` là **đối tượng thực tế**, miễn đoạn code đã qua kiểm tra của compiler.

## 2. Quy tắc chọn handler chi tiết

Dạng thường gặp:

```java
try {
    operation();
} catch (SpecificException e) {
    handleSpecific(e);
} catch (GeneralException e) {
    handleGeneral(e);
}
```

Đây là **mẫu cú pháp**, các lớp `SpecificException`/`GeneralException` và phương thức xử lý chưa được định nghĩa; không phải chương trình chạy độc lập.

Khi `operation()` ném một `Throwable` và lỗi thoát khỏi câu lệnh đó, Java tìm handler phù hợp theo chuỗi:

1. Xác định đối tượng `Throwable` đang bị ném, ví dụ một `NumberFormatException`.
2. Xem các handler thuộc khối `try` liên quan, theo thứ tự khai báo.
3. Với mỗi `catch (T e)`, kiểm tra đối tượng có phải instance của `T` hoặc lớp con của `T` không. Với multi-catch, chỉ cần khớp một trong các loại được khai báo.
4. Chọn handler **đầu tiên** thỏa điều kiện; biến `e` tham chiếu đến đối tượng đang được ném.
5. Nếu không có handler phù hợp ở phạm vi hiện tại, lỗi lan ra phương thức gọi; Java tiếp tục tìm handler bên ngoài và xử lý các bước dọn dẹp theo quy tắc.
6. Nếu lỗi không được xử lý đến ranh giới của thread, cơ chế uncaught exception của thread nhận được nó; không nên coi mọi ngoại lệ không bắt là lý do toàn bộ JVM phải kết thúc ngay.

**Điều quan trọng:** `catch` không dùng phép so sánh kiểu “phải giống hệt”. Nó hỗ trợ **đa hình**: handler khai báo kiểu cha có thể bắt exception thuộc một lớp con.

### 2.1. Phép kiểm tra tương thích kiểu

Về mặt trực giác, có thể hình dung quy tắc chọn một handler đơn như:

```java
// Minh họa tư duy, không phải implementation của JVM.
if (thrown instanceof HandlerType) {
    // Handler tương ứng có thể nhận đối tượng bị ném.
}
```

Đoạn trên là mã **minh họa**, `HandlerType` đại diện tên lớp catch thực tế. Java biên dịch handler thành cơ chế xử lý ngoại lệ, không chạy nguyên văn đoạn `if` này.

Một `catch (Exception e)` có thể bắt `IllegalArgumentException`, `IOException` hoặc `NullPointerException` vì tất cả là lớp con của `Exception`. Nhưng không bắt `AssertionError`, vì `AssertionError extends Error` và `Error` không kế thừa `Exception`.

### 2.2. Bảng kiểm tra nhanh

| Đối tượng thực tế được ném | `catch (NumberFormatException)` | `catch (IllegalArgumentException)` | `catch (Exception)` | `catch (Error)` |
| --- | --- | --- | --- | --- |
| `new NumberFormatException()` | Có | Có | Có | Không |
| `new IllegalArgumentException()` | Không | Có | Có | Không |
| `new IllegalStateException()` | Không | Không | Có | Không |
| `new java.io.IOException()` | Không | Không | Có | Không |
| `new AssertionError()` | Không | Không | Không | Có |

Bảng ghi **khả năng tương thích về kiểu**. Không thể ghép tùy ý mọi handler vào một `try` rồi hy vọng chương trình luôn biên dịch: Java còn kiểm tra thứ tự unreachable handler và tình trạng các checked exception có thể được ném từ vùng `try`.

## 3. Chương trình Java 17: cùng kiểu biến, nhiều kiểu đối tượng thực tế

Ví dụ này chứng minh điểm dễ nhầm nhất: tất cả phần tử trong mảng có **kiểu biến là `Exception`**, nhưng các đối tượng thực tế thuộc ba lớp khác nhau, nên mỗi lần ném có thể đi vào nhánh `catch` khác nhau.

Lưu tệp `CatchRuntimeTypeDemo.java`:

```java
import java.io.IOException;

public class CatchRuntimeTypeDemo {
    static void propagate(Exception problem) throws Exception {
        throw problem;
    }

    public static void main(String[] args) {
        Exception[] problems = {
                new NumberFormatException("không phải số"),
                new IllegalStateException("trạng thái sai"),
                new IOException("không đọc được")
        };

        for (Exception problem : problems) {
            try {
                propagate(problem);
            } catch (NumberFormatException e) {
                System.out.println("Parse: "
                        + e.getClass().getSimpleName());
            } catch (IOException e) {
                System.out.println("I/O: "
                        + e.getClass().getSimpleName());
            } catch (Exception e) {
                System.out.println("Còn lại: "
                        + e.getClass().getSimpleName());
            }
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 CatchRuntimeTypeDemo.java
java CatchRuntimeTypeDemo
```

**Stdout chính xác:**

```text
Parse: NumberFormatException
Còn lại: IllegalStateException
I/O: IOException
```

Giải thích từng trường hợp:

- `problem` có **kiểu khai báo `Exception`** trong cả ba vòng lặp.
- Ở lượt đầu, đối tượng thực tế là `NumberFormatException`. Handler cụ thể nhận nó.
- Lượt hai là `IllegalStateException`. Nó không phải `NumberFormatException` hoặc `IOException`, nên được handler tổng quát `catch (Exception e)` nhận.
- Lượt ba là `IOException`. Handler `catch (IOException e)` nhận nó.
- `propagate(Exception problem) throws Exception` thực thi `throw problem;` và khai báo kiểu checked rộng `Exception`. Đây là ví dụ cố tình dùng chữ ký tổng quát để làm rõ khác biệt; trong API thực tế nên khai báo kiểu hẹp, có nghĩa nếu có thể.

Chúng ta **không cần ép kiểu** từ `Exception` sang `NumberFormatException` trước khi ném. Chỉ cần biến tham chiếu đến đúng đối tượng; kiểu runtime sẽ được sử dụng trong việc chọn handler.

### 3.1. Sự khác nhau giữa kiểu biến và kiểu đối tượng

Mẫu cốt lõi:

```java
Exception declaredAsException =
        new NumberFormatException("bad input");
// declaredAsException.getClass() là NumberFormatException.class.
```

Đây là đoạn minh họa trong method. Phía trái `Exception` là **kiểu tĩnh** mà compiler sử dụng để kiểm tra thao tác có thể gọi, đặc biệt đối với checked exceptions. Phía phải tạo đối tượng **`NumberFormatException`**. Không có “chuyển đổi” khiến đối tượng thành một `Exception` chung hơn; tham chiếu được gán lên kiểu cha nhưng đối tượng vẫn giữ đúng lớp của nó.

Gọi `getClass()` là cách quan sát kiểu runtime của đối tượng. Phép kiểm tra `instanceof` cũng phản ánh quan hệ instance/subtype, nhưng thường không cần tự dùng để chọn handler, vì cấu trúc `catch` đã giải quyết công việc đó.

## 4. Kế thừa quyết định phạm vi một handler

Cây phân cấp đơn giản:

```text
Throwable
├── Error
│   └── AssertionError
└── Exception
    ├── IOException
    │   ├── FileNotFoundException
    │   └── EOFException
    └── RuntimeException
        ├── IllegalArgumentException
        │   └── NumberFormatException
        └── IllegalStateException
```

Một `catch (IOException e)` có thể nhận `FileNotFoundException` hoặc `EOFException`. Tuy nhiên nó **không** nhận `NumberFormatException`, dù cả hai nhánh cuối cùng đều kế thừa `Exception`.

Ví dụ:

```java
try {
    Integer.parseInt("abc");
} catch (NumberFormatException e) {
    System.out.println("Chuỗi không phải số");
} catch (IllegalArgumentException e) {
    System.out.println("Tham số sai theo quy tắc khác");
}
```

Đây là một **đoạn bên trong method**. `NumberFormatException` kế thừa `IllegalArgumentException` nên cả hai handler **đều tương thích về kiểu**, nhưng handler cụ thể đứng trước nhận đối tượng. Handler sau sẽ chỉ được xét nếu handler trước không tương thích với ngoại lệ khác được ném.

### 4.1. Vì sao phải đặt `catch` của lớp con trước lớp cha?

Đoạn sau **cố ý sai, không biên dịch**:

```java
try {
    Integer.parseInt("abc");
} catch (IllegalArgumentException e) {
    System.out.println("Đã bắt");
} catch (NumberFormatException e) {
    System.out.println("Không thể tới đây");
}
```

`IllegalArgumentException` đã bao phủ `NumberFormatException`. Handler thứ hai không có cơ hội nhận đối tượng nào còn sót lại thuộc loại `NumberFormatException`, nên Java báo lỗi như “exception NumberFormatException has already been caught”.

Sắp đúng từ **cụ thể đến tổng quát**:

```java
try {
    Integer.parseInt("abc");
} catch (NumberFormatException e) {
    System.out.println("Sai định dạng số");
} catch (IllegalArgumentException e) {
    System.out.println("Sai tham số");
}
```

Cũng là đoạn minh họa, không có `main`. Thứ tự ở đây không làm thay đổi kiểu đối tượng; nó quyết định **handler đầu tiên có cơ hội nhận**.

### 4.2. Mỗi ngoại lệ chỉ đi vào một nhánh `catch` của cùng một `try`

Khi handler phù hợp đã được chọn, Java không tự thực thi các `catch` còn lại như chuỗi `if`. Nếu cần phân loại chi tiết trước khi gom về xử lý chung, hãy để handler cụ thể gọi một phương thức xử lý chung, hoặc chuẩn hóa thông tin tại một tầng có trách nhiệm. Không trông chờ `catch (Exception)` phía sau được chạy tiếp sau `catch (NumberFormatException)`.

## 5. Checked exception ảnh hưởng compiler, không đổi matching runtime

**Checked** và **unchecked** là quy tắc về việc có bắt buộc bắt hoặc khai báo khi biên dịch hay không:

- Checked exception gồm các lớp con của `Throwable` không thuộc nhánh `RuntimeException` hoặc `Error`, ví dụ `IOException`.
- Unchecked gồm `RuntimeException`, `Error` và các lớp con, ví dụ `NumberFormatException` hoặc `AssertionError`.

Dù `NumberFormatException` là unchecked, `catch (Exception e)` vẫn bắt được, bởi `NumberFormatException extends RuntimeException extends Exception`.

Ngược lại, một lớp tự tạo **kế thừa trực tiếp `Throwable`** có thể là checked nhưng `catch (Exception)` vẫn không bắt được, vì lớp đó không ở dưới `Exception`. Do vậy:

**Việc một exception là checked hay unchecked không phải tiêu chí chọn handler ở runtime. Quan hệ kiểu kế thừa mới là tiêu chí.**

### 5.1. Kiểu tĩnh ảnh hưởng nghĩa vụ `throws`

Ví dụ:

```java
// Đây là đoạn minh họa nằm trong method, không phải chương trình đầy đủ.
Exception e = new NumberFormatException("sai số");
// throw e; // Nếu bỏ comment: thông thường cần bắt hoặc khai báo Exception.
```

Vì biểu thức `e` có **kiểu khai báo `Exception`**, compiler xét `throw e;` theo kiểu checked rộng này, mặc dù đối tượng cụ thể ở lần chạy minh họa là unchecked. Đây không phải dấu hiệu rằng `catch` sẽ chọn theo `Exception`: ở runtime, nếu đối tượng được ném vẫn là `NumberFormatException` thì `catch (NumberFormatException)` vẫn khớp.

Cần phân biệt với **precise rethrow** từ Java 7: trong một số điều kiện với biến tham số của `catch` không bị gán lại, compiler có thể suy luận tập checked exception cụ thể khi gặp `throw e;`. Đó là một quy tắc đặc biệt của ngôn ngữ cho việc phân tích checked exception, không thay đổi cách chọn handler runtime.

### 5.2. Ví dụ `throws IOException` nhưng runtime là lớp con

Method:

```java
static void load() throws java.io.IOException {
    throw new java.io.FileNotFoundException("Không tìm thấy dữ liệu");
}
```

Đoạn minh họa trên hợp lệ vì `FileNotFoundException extends IOException`. Caller có thể viết `catch (FileNotFoundException e)` trước `catch (IOException e)` nếu khối `try` có thể nhận lỗi loại tương ứng. Khi thực thi, nếu chính đối tượng `FileNotFoundException` được ném, handler hẹp được chọn. Tên `IOException` trong mệnh đề `throws` chỉ công bố kiểu checked mà caller phải xem xét; nó không “biến” đối tượng thành lớp `IOException`.

## 6. Chương trình Java 17: nhiều lớp con của `IOException`

Ví dụ thứ hai nhấn mạnh việc **cùng một method khai báo `throws IOException`**, nhưng mỗi lần chạy có thể ném một subtype khác nhau; handler xử lý theo kiểu thực tế.

Lưu `CatchIoSubtypeDemo.java`:

```java
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;

public class CatchIoSubtypeDemo {
    static void read(String mode) throws IOException {
        switch (mode) {
            case "missing" ->
                    throw new FileNotFoundException("Thiếu tệp");
            case "empty" ->
                    throw new EOFException("Hết dữ liệu");
            default ->
                    throw new IOException("Lỗi I/O chung");
        }
    }

    public static void main(String[] args) {
        for (String mode : new String[] {"missing", "empty", "other"}) {
            try {
                read(mode);
            } catch (FileNotFoundException e) {
                System.out.println(mode + ": thiếu tệp");
            } catch (EOFException e) {
                System.out.println(mode + ": hết dữ liệu");
            } catch (IOException e) {
                System.out.println(mode + ": lỗi I/O chung");
            }
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 CatchIoSubtypeDemo.java
java CatchIoSubtypeDemo
```

Stdout:

```text
missing: thiếu tệp
empty: hết dữ liệu
other: lỗi I/O chung
```

Diễn giải:

- Ở chế độ `"missing"`, method tạo `FileNotFoundException` và handler `FileNotFoundException` nhận nó.
- Ở chế độ `"empty"`, method tạo `EOFException` và handler `EOFException` nhận nó.
- Ở chế độ `"other"`, method tạo chính `IOException` và handler tổng quát `IOException` nhận nó.
- Cả ba đều thỏa khai báo `throws IOException`; khai báo này không khống chế mọi lời gọi phải ném cùng một subtype.
- Khi có một handler cụ thể khớp, nhánh `catch (IOException)` cuối không chạy tiếp.

**Điểm thiết kế:** bắt subtype riêng khi caller có hành động riêng, ví dụ cho phép chọn file khác nếu không tìm thấy, kết thúc đọc khi gặp EOF theo hợp đồng phù hợp, hoặc dừng thao tác với lỗi I/O khác. Nếu không có hành động khác nhau, một `catch (IOException)` chung có thể là đủ.

## 7. Kiểu runtime quyết định matching, nhưng thứ tự handler vẫn quan trọng

Khi nhiều handler cùng tương thích với một đối tượng, Java chọn handler **đầu tiên trong cùng chuỗi `catch`**. Tuy nhiên compiler loại trừ nhiều sắp xếp khiến nhánh sau không thể tới được.

Xét `new NumberFormatException()`:

```text
catch (NumberFormatException e)  ← phù hợp và đứng trước: nhận lỗi
catch (IllegalArgumentException e) ← cũng tương thích nhưng không chạy
catch (Exception e)              ← cũng tương thích nhưng không chạy
```

Nếu thay thứ tự và đặt `catch (Exception e)` lên đầu, các handler lớp con sau sẽ bị che và compile error. Vì vậy quy tắc thực hành vừa đúng ngữ nghĩa vừa giúp compiler chấp nhận mã là:

> **Đặt `catch` của lớp con/cụ thể trước, `catch` của lớp cha/tổng quát sau.**

Không phải các lớp exception nào cũng có quan hệ cha–con. Chẳng hạn `FileNotFoundException` và `NumberFormatException` không bao phủ nhau; về mặt kiểu, có thể đặt chúng theo thứ tự nào cũng được nếu khối `try` có thể phát sinh các kiểu checked tương ứng. Thứ tự quan trọng nhất khi có **quan hệ bao hàm**.

### 7.1. Một lưu ý về các nested `try`

Java không so sánh mọi `catch` trong cả chương trình cùng lúc. Handler ở `try` **gần nơi phát sinh lỗi** được xét trước trong phạm vi điều khiển tương ứng; nếu không có handler phù hợp, lỗi mới lan ra ngoài.

Ví dụ:

```java
try {
    try {
        Integer.parseInt("abc");
    } catch (IllegalArgumentException e) {
        System.out.println("Bắt bên trong");
    }
} catch (NumberFormatException e) {
    System.out.println("Bắt bên ngoài");
}
```

Đây là đoạn minh họa đặt trong method. Vì `IllegalArgumentException` bên trong nhận `NumberFormatException`, handler ngoài không được thực hiện. Ngoại lệ đã được xử lý trong phạm vi gần hơn; không có “cơ chế chọn handler cụ thể nhất của toàn bộ chương trình”.

Nếu handler trong **ném lại** exception, nó có thể lan ra `catch` của `try` bao ngoài, nhưng khi đó là một bước lan truyền tiếp theo, không phải Java bỏ qua handler trong để tự chọn handler ngoài.

## 8. Multi-catch cũng áp dụng theo kiểu đối tượng

Từ Java 7, có thể gộp các handler có cùng logic:

```java
catch (java.io.IOException | IllegalArgumentException e) {
    System.out.println("Không xử lý được yêu cầu: " + e.getMessage());
}
```

Đoạn trên là phần `catch` minh họa, phải đi sau một `try` phù hợp. Multi-catch khớp nếu đối tượng là một instance của **ít nhất một** kiểu được liệt kê. `IOException` và `IllegalArgumentException` thuộc hai nhánh riêng nên có thể viết chung.

Java **không cho phép** các kiểu trong cùng một multi-catch có quan hệ kế thừa trực tiếp hoặc gián tiếp theo kiểu một nhánh đã bao trùm nhánh kia. Ví dụ sai:

```java
// KHÔNG hợp lệ:
catch (NumberFormatException | IllegalArgumentException e) {
    System.out.println(e);
}
```

`NumberFormatException extends IllegalArgumentException` nên khai báo hai lựa chọn này là thừa và bị compiler từ chối.

### 8.1. Chương trình Java 17 chạy độc lập với multi-catch

Lưu `MultiCatchRuntimeDemo.java`:

```java
import java.io.IOException;

public class MultiCatchRuntimeDemo {
    static void execute(String input) throws IOException {
        if (input == null) {
            throw new IOException("Nguồn trống");
        }
        int number = Integer.parseInt(input);
        if (number <= 0) {
            throw new IllegalArgumentException("Phải dương");
        }
        System.out.println("OK: " + number);
    }

    public static void main(String[] args) {
        for (String input : new String[] {"5", "abc", "0", null}) {
            try {
                execute(input);
            } catch (IOException | IllegalArgumentException e) {
                System.out.println("Lỗi: "
                        + e.getClass().getSimpleName());
            }
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 MultiCatchRuntimeDemo.java
java MultiCatchRuntimeDemo
```

Stdout:

```text
OK: 5
Lỗi: NumberFormatException
Lỗi: IllegalArgumentException
Lỗi: IOException
```

`NumberFormatException` được multi-catch nhận **qua nhánh `IllegalArgumentException`**. `IOException` được nhận qua nhánh kia. Cùng một khối xử lý dùng `getClass().getSimpleName()` để quan sát subtype thực sự, nên tên in ra vẫn khác nhau.

Biến `e` trong multi-catch là **implicitly final**, không được gán lại. Kiểu tĩnh chung của `e` cũng không nhất thiết là từng kiểu con cụ thể; nếu cần API riêng cho một subtype, nên dùng handler riêng hoặc kiểm tra kiểu cẩn thận.

## 9. `catch (Exception)` không bắt mọi `Throwable`

Một điều kiện cơ bản về cây phân cấp:

```text
Throwable
├── Exception
│   └── RuntimeException
│       └── NumberFormatException
└── Error
    └── AssertionError
```

`catch (Exception e)` có thể xử lý `NumberFormatException`, nhưng **không** bắt `AssertionError` vì `Error` không kế thừa `Exception`. `catch (Throwable t)` có thể bắt cả hai **về mặt kiểu**, nhưng không nên dùng như giải pháp “đỡ mọi lỗi” trong mã ứng dụng.

### 9.1. Minh họa không gây lỗi JVM thật

Lưu `CatchThrowableBranchesDemo.java`:

```java
public class CatchThrowableBranchesDemo {
    static void fail(boolean useError) {
        if (useError) {
            throw new AssertionError("Tình huống kiểm thử");
        }
        throw new IllegalStateException("Trạng thái không hợp lệ");
    }

    public static void main(String[] args) {
        for (boolean useError : new boolean[] {false, true}) {
            try {
                fail(useError);
            } catch (Exception e) {
                System.out.println("Exception: "
                        + e.getClass().getSimpleName());
            } catch (Error e) {
                System.out.println("Error: "
                        + e.getClass().getSimpleName());
            }
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 CatchThrowableBranchesDemo.java
java CatchThrowableBranchesDemo
```

Stdout:

```text
Exception: IllegalStateException
Error: AssertionError
```

Ví dụ **chủ động tạo `AssertionError` để giải thích hệ kiểu**; đây không phải khuyến nghị bắt `Error` để cố hồi phục sau lỗi VM như `OutOfMemoryError` hoặc `StackOverflowError`. Đừng tạo các lỗi VM thực chỉ để thử handler, vì có thể làm tiến trình không còn đủ tài nguyên để chạy bình thường.

### 9.2. Checked/unchecked không quyết định handler

- `IOException` là checked và nằm trong `Exception`.
- `IllegalStateException` là unchecked và cũng nằm trong `Exception`.
- `AssertionError` là unchecked nhưng nằm trong `Error`.

Cả checked và unchecked đều tuân cùng nguyên tắc chọn `catch` theo **runtime type**. Phân loại checked/unchecked chỉ quy định một số nghĩa vụ lúc biên dịch.

## 10. Wrapper và `cause`: Java bắt lớp ở ngoài đang được ném

Một ngoại lệ có thể chứa một ngoại lệ khác làm **nguyên nhân** (`cause`). Ví dụ:

```java
throw new IllegalStateException(
        "Không hoàn tất thao tác",
        new java.io.IOException("Nguồn dữ liệu lỗi"));
```

Ở đây:

- Đối tượng **đang được ném** là `IllegalStateException`.
- `getCause()` của nó trỏ đến `IOException`.
- Handler `catch (IllegalStateException)` hoặc `catch (Exception)` có thể nhận wrapper.
- Handler `catch (IOException)` không tự lấy `cause` để bắt; muốn xử lý nguyên nhân thì cần kiểm tra sau khi đã nhận wrapper ở tầng thích hợp.

Cơ chế này tránh hiểu lầm rằng Java tự “bóc” chuỗi nguyên nhân để đi chọn handler. **Nó so kiểu của đối tượng đang được ném**, không đi tìm kiểu phù hợp sâu nhất trong `getCause()`.

### 10.1. Chương trình Java 17 chứng minh wrapper/cause

Tạo `CatchWrapperTypeDemo.java`:

```java
import java.io.IOException;

public class CatchWrapperTypeDemo {
    static void load() {
        IOException original = new IOException("Không đọc được");
        throw new IllegalStateException("Không tải được dữ liệu", original);
    }

    public static void main(String[] args) {
        try {
            load();
        } catch (IllegalStateException e) {
            System.out.println("Đã bắt: "
                    + e.getClass().getSimpleName());
            System.out.println("Cause: "
                    + e.getCause().getClass().getSimpleName());
            System.out.println("Message: " + e.getMessage());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 CatchWrapperTypeDemo.java
java CatchWrapperTypeDemo
```

Stdout:

```text
Đã bắt: IllegalStateException
Cause: IOException
Message: Không tải được dữ liệu
```

Trong đoạn này, `IOException` chỉ được **tạo và truyền vào constructor** làm nguyên nhân; nó không tự lan ra khỏi `load()` như đối tượng exception chính. Vì vậy `load()` không cần khai báo `throws IOException`. Handler ở `main` nhận đúng `IllegalStateException`.

**Tình huống ngược:** nếu wrapper là một `IOException` với cause là `IllegalStateException` thì handler được chọn vẫn theo lớp của wrapper `IOException`. Cause giữ thông tin chẩn đoán và ngữ cảnh, không thay thế cơ chế matching.

### 10.2. Rethrow có thay đổi kiểu runtime không?

`throw e;` ném lại **chính đối tượng** mà `e` tham chiếu. Nó không tự chuyển đối tượng về kiểu khai báo của biến.

Ví dụ:

```java
// Minh họa trong method có throws Exception.
Exception e = new NumberFormatException("Sai số");
throw e;
```

Nếu runtime ném thành công, đối tượng vẫn là `NumberFormatException`. Khai báo `throws Exception` cần thiết do việc kiểm tra kiểu tĩnh trong trường hợp điển hình này, nhưng handler tương thích tại caller sẽ xét loại đối tượng thực tế.

Ngược lại, `throw new IllegalStateException("...", e)` tạo wrapper mới: **kiểu runtime của ngoại lệ đang lan truyền đã trở thành `IllegalStateException`**, còn `e` là cause. Đó là sự khác biệt lớn khi quyết định nên ném lại hay chuyển đổi ngoại lệ.

## 11. Ngoại lệ phát sinh trong `catch` có thể được bắt bởi nhánh `catch` kế bên không?

Không. Các nhánh `catch` của cùng một `try` là những **handler thay thế**, chứ không bao quanh nhau. Một ngoại lệ mới được ném **trong chính handler** sẽ không được những handler kế tiếp gắn với cùng `try` tự bắt.

Ví dụ:

```java
try {
    try {
        throw new NumberFormatException("bad");
    } catch (NumberFormatException e) {
        throw new IllegalStateException("Bọc lỗi", e);
    } catch (IllegalStateException e) {
        System.out.println("Không bắt wrapper từ catch phía trên");
    }
} catch (IllegalStateException e) {
    System.out.println("Wrapper được bắt ở try ngoài");
}
```

Đây là đoạn minh họa trong method. Handler `NumberFormatException` được chọn khi exception gốc được ném; handler đó lại tạo `IllegalStateException` mới. Handler `IllegalStateException` còn lại của **cùng `try` trong** không chạy; handler tương thích của `try` **ngoài** nhận wrapper.

Cần lưu ý compiler chỉ kiểm tra các nhánh unreachable theo quy tắc kiểu, còn sự khác nhau giữa lỗi ném trong `try` và lỗi ném trong `catch` là quy tắc **phạm vi xử lý**. Đừng suy luận rằng hai nhánh handler là một chuỗi xử lý tuần tự.

## 12. Vai trò của `finally` và try-with-resources

Khi exception được ném và một handler phù hợp được tìm thấy, các khối `finally` liên quan được thực thi theo quy tắc Java khi rời khỏi phạm vi. Cơ chế này hỗ trợ dọn dẹp nhưng **không tự quyết định lại** loại exception nào đang được lan truyền.

```java
try {
    throw new NumberFormatException("bad");
} catch (IllegalArgumentException e) {
    System.out.println("Đã bắt ngoại lệ con qua kiểu cha");
} finally {
    System.out.println("Đã dọn dẹp");
}
```

Đoạn minh họa trên in:

```text
Đã bắt ngoại lệ con qua kiểu cha
Đã dọn dẹp
```

`NumberFormatException` thực sự được ném, nhưng handler `IllegalArgumentException` vẫn nhận được vì quan hệ kế thừa.

**Ngoại lệ quan trọng:** nếu `finally` tự ném một exception khác hoặc thực hiện `return`, nó có thể thay thế/che mất kết quả hoặc exception đang chờ lan truyền. Nên tránh viết `return` trong `finally` hoặc tự ý ném lỗi mới từ phần dọn dẹp nếu mục tiêu là bảo toàn nguyên nhân gốc.

Với try-with-resources, nếu thân khối và `close()` đều ném exception, Java thường giữ lỗi từ thân khối làm exception chính và gắn lỗi khi đóng vào `getSuppressed()`. `catch` của luồng ngoài so với **exception chính đang được ném**, không so trực tiếp với suppressed exceptions.

## 13. Các câu hỏi kiểm tra nhanh

### 13.1. Biến `Exception` có thể bị `catch (IOException)` bắt không?

**Có**, nếu đối tượng thực tế được ném là `IOException` hoặc subtype và đoạn code hợp lệ theo quy tắc checked exception. Trong `CatchRuntimeTypeDemo`, `problem` có kiểu biến `Exception`, song lượt thứ ba được `catch (IOException)` nhận đúng.

### 13.2. `catch (Exception)` có bắt tất cả unchecked exceptions không?

**Không.** Nó bắt các unchecked exception thuộc nhánh `RuntimeException`, vì `RuntimeException extends Exception`. Nó không bắt các loại unchecked thuộc nhánh `Error`, ví dụ `AssertionError`.

### 13.3. `catch (Throwable)` có bắt mọi lỗi bất kể nơi phát sinh không?

**Không.** Nó là handler rộng theo hệ kiểu Java, nhưng chỉ xử lý các `Throwable` lan đến handler trên **luồng điều khiển đang được bảo vệ**. Nó không tự bắt exception xảy ra trên một thread khác, không bảo vệ khỏi JVM/process bị chấm dứt bất thường và không rollback side effect.

### 13.4. Handler hẹp nhất luôn được chọn dù nằm sau handler rộng hơn?

**Không.** Java dùng handler tương thích **đầu tiên trong phạm vi `catch` tương ứng**. Compiler chặn nhiều thứ tự khiến handler sau bị che bởi handler rộng ở trước. Java không đi tìm “handler nhỏ nhất trên toàn call stack” rồi bỏ qua handler gần hơn.

### 13.5. Nếu `getCause()` là `IOException`, `catch (IOException)` có bắt wrapper `IllegalStateException` không?

**Không.** `cause` là quan hệ nguyên nhân để chẩn đoán, không tham gia trực tiếp việc chọn `catch`. Muốn xử lý có ngữ nghĩa theo cause, trước hết bắt wrapper phù hợp rồi kiểm tra cause có chủ đích.

### 13.6. `catch (IllegalArgumentException)` bắt `NumberFormatException` thì `e.getClass()` trả gì?

Nó trả **`NumberFormatException.class`**, vì biến `e` vẫn tham chiếu đối tượng con. Java không thay đổi lớp thực tế của đối tượng chỉ vì handler có kiểu khai báo rộng hơn.

### 13.7. Một exception có thể được xử lý bởi hai handler cùng một khối `try` không?

**Không tự động.** Một exception đang được ném chọn một handler đầu tiên. Nếu handler ném lại hoặc tạo exception mới, một handler ở phạm vi bao ngoài có thể tiếp tục nhận, nhưng không phải nhánh kế tiếp của cùng chuỗi `catch`.

## 14. Các sai lầm thường gặp

| Nhầm lẫn | Điều đúng |
| --- | --- |
| “Biến có kiểu `Exception` thì chỉ `catch (Exception)` được bắt.” | Kiểu thực tế của đối tượng có thể là subtype; handler hẹp tương thích vẫn nhận được. |
| “`throws IOException` làm exception thực tế đổi thành `IOException`.” | Mệnh đề `throws` khai báo hợp đồng checked; đối tượng `FileNotFoundException` vẫn giữ nguyên lớp của nó. |
| “`catch` chỉ nhận đúng cùng tên class.” | Kiểu cha cũng nhận được đối tượng thuộc lớp con. |
| “Các `catch` chạy lần lượt như nhiều phép `if` độc lập.” | Chỉ handler tương thích đầu tiên của cùng `try` được thực hiện. |
| “`catch (Exception)` đứng trước `catch (NumberFormatException)` là hợp lệ vì rộng hơn.” | Nhánh sau bị che và bị compiler từ chối. |
| “Unchecked exception không thể bị `catch`.” | Unchecked không bắt buộc xử lý compile-time nhưng vẫn có thể được bắt. |
| “`catch (Exception)` bắt `AssertionError` vì cũng là Throwable.” | `AssertionError` nằm dưới `Error`, là nhánh khác `Exception`. |
| “Handler tự tìm `getCause()` để chọn lớp lỗi phù hợp.” | Handler chỉ đối chiếu kiểu của đối tượng đang được ném. |
| “Exception ném trong `catch` sẽ được nhánh `catch` tiếp theo xử lý.” | Nó thoát ra phạm vi ngoài nếu không được xử lý bởi một `try` mới. |
| “Bắt được lỗi thì toàn bộ trạng thái trước đó trở về như cũ.” | `catch` không tự rollback thay đổi dữ liệu, I/O hoặc giao dịch. |
| “Bắt `Throwable` mọi nơi sẽ an toàn hơn.” | Có thể che lỗi nghiêm trọng của runtime và khiến chương trình tiếp tục trên trạng thái không tin cậy. |
| “Stack trace là một danh sách handler đã thử.” | Stack trace phản ánh chuỗi lời gọi và nguồn gốc lỗi; không phải log đầy đủ thứ tự thử từng nhánh `catch`. |

## 15. Bài tập thực hành

### Bài tập 1 — Dự đoán handler theo lớp thực tế

Cho đoạn minh họa:

```java
Exception problem = new NumberFormatException("Sai");
try {
    throw problem;
} catch (NumberFormatException e) {
    System.out.println("A");
} catch (IllegalArgumentException e) {
    System.out.println("B");
} catch (Exception e) {
    System.out.println("C");
}
```

Đây là đoạn code đặt trong phương thức đã xử lý nghĩa vụ checked exception tại handler. **Đáp án:** in `A`. Dù `problem` là biến kiểu `Exception`, đối tượng thực tế thuộc `NumberFormatException`. Nếu bỏ nhánh `catch (NumberFormatException)`, kết quả trở thành `B`; nếu bỏ tiếp `catch (IllegalArgumentException)`, kết quả trở thành `C`.

### Bài tập 2 — Sửa lỗi unreachable handler

Từ:

```java
try {
    Integer.parseInt("x");
} catch (RuntimeException e) {
    System.out.println("Rộng");
} catch (NumberFormatException e) {
    System.out.println("Hẹp");
}
```

Hãy đổi thứ tự để handler `NumberFormatException` đứng trước. Giải thích vì sao đoạn gốc không biên dịch: `NumberFormatException` là lớp con của `RuntimeException` và đã bị handler rộng bao phủ.

### Bài tập 3 — Thay wrapper và dự đoán nhánh xử lý

Trong `CatchWrapperTypeDemo`, thử thay `IllegalStateException` bằng `java.io.UncheckedIOException` có cause là `IOException`, rồi thay handler ngoài để bắt `UncheckedIOException` hoặc `RuntimeException`. Giải thích vì sao handler `catch (IOException)` không tự nhận wrapper. Nếu dùng constructor thực tế `new UncheckedIOException(original)`, nhớ import `java.io.UncheckedIOException`.

### Bài tập 4 — Đọc exception qua nhiều tầng

Viết ba method `main() → service() → parse()`. Trong `parse()` ném `NumberFormatException`; trong `service()` khai báo `throws Exception` hoặc chuyển tiếp exception phù hợp; tại `main()` bắt riêng `NumberFormatException`. Giải thích **kiểu static được compiler đọc ở từng lời gọi** và **kiểu runtime được handler so khớp**.

### Bài tập 5 — Thay chuỗi `catch` bằng multi-catch

Với `MultiCatchRuntimeDemo`, thay handler multi-catch bằng hai handler riêng `IOException` và `IllegalArgumentException` để mỗi loại hiển thị thông điệp khác nhau. Có cần thêm handler riêng cho `NumberFormatException` không? **Không bắt buộc** vì `IllegalArgumentException` đã bao phủ nó, nhưng có thể thêm **trước** handler cha nếu cần báo lỗi parse riêng.

### Bài tập 6 — Lỗi ném trong handler

Dự đoán stdout của ví dụ ở Mục 11. Giải thích tại sao handler ngoài nhận được `IllegalStateException` mới và vì sao handler `catch (IllegalStateException)` còn lại của `try` trong không chạy.

## 16. Checklist hiểu đúng

- [ ] Phân biệt được **kiểu biến**, **kiểu khai báo trong `throws`** và **kiểu runtime của đối tượng**.
- [ ] Vẽ được quan hệ `NumberFormatException → IllegalArgumentException → RuntimeException → Exception → Throwable`.
- [ ] Giải thích vì sao `catch (IOException)` bắt được `FileNotFoundException` và `EOFException`.
- [ ] Đặt handler cho lớp con trước handler lớp cha để tránh unreachable catch.
- [ ] Chạy `CatchRuntimeTypeDemo.java` và giải thích cả ba dòng stdout.
- [ ] Chạy `CatchIoSubtypeDemo.java` và dự đoán đúng handler cho từng subtype.
- [ ] Viết multi-catch với hai kiểu không có quan hệ kế thừa và nêu lý do không được dùng các kiểu bao trùm nhau.
- [ ] Biết `catch (Exception)` bắt unchecked `RuntimeException` nhưng không bắt `Error`.
- [ ] Phân biệt wrapper và cause; handler dựa trên wrapper đang được ném, không tự đi xuống `cause`.
- [ ] Nhận ra exception ném từ một `catch` cần handler ở phạm vi khác phù hợp.
- [ ] Không nhầm `finally`/try-with-resources với cơ chế chọn handler hoặc rollback.
- [ ] Thiết kế `catch` theo khả năng xử lý thực sự, không nuốt lỗi một cách vô điều kiện.

**Ghi nhớ cuối:** **Runtime type** của đối tượng `Throwable` đang được ném quyết định những `catch` nào tương thích; phạm vi `try` và thứ tự các nhánh quyết định handler được chọn; compiler kiểm tra các quy tắc kiểu và checked exception trước khi chương trình chạy. Hiểu đồng thời cả ba giúp dự đoán đúng luồng xử lý lỗi trong Java.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="try-catch-003">Nhiều khối catch và thứ tự từ cụ thể đến tổng quát</a>

<details>
<summary>Xem chi tiết</summary>

# Nhiều khối catch và thứ tự từ cụ thể đến tổng quát trong Java

> Java Core – Exception · Nhóm: **Try và catch** · Các ví dụ chạy độc lập dùng **Java 17+**.

## 1. Vấn đề cần giải quyết

Một thao tác Java có thể thất bại theo nhiều cách: tệp không tồn tại, lỗi đọc, dữ liệu sai kiểu, trạng thái ứng dụng không hợp lệ. Nếu các tình huống cần phản ứng khác nhau, ta có thể đặt **nhiều khối `catch` nối tiếp sau cùng một `try`**:

```java
try {
    // Thực hiện thao tác có thể phát sinh nhiều loại exception.
} catch (SpecificException ex) {
    // Xử lý loại cụ thể.
} catch (GeneralException ex) {
    // Xử lý loại rộng hơn.
}
```

Đây là **cú pháp minh họa**, chưa phải chương trình độc lập: `SpecificException` và `GeneralException` là tên tượng trưng.

**Nguyên tắc quan trọng:** Java kiểm tra các nhánh `catch` theo **thứ tự từ trên xuống dưới**; khi một ngoại lệ được ném, **khối phù hợp đầu tiên** sẽ được chọn. Vì vậy các lớp ngoại lệ có quan hệ kế thừa phải được đặt **từ cụ thể (lớp con) đến tổng quát (lớp cha)** để handler cụ thể còn cơ hội được thực thi.

Sau bài này, bạn có thể:

1. Dùng cây kế thừa ngoại lệ để xác định thứ tự các `catch` hợp lệ.
2. Giải thích vì sao `catch (IOException)` đặt trước `catch (FileNotFoundException)` sẽ lỗi biên dịch.
3. Dự đoán chính xác handler nào chạy với checked hoặc unchecked exception.
4. Phân biệt **nhiều catch nối tiếp** với **multi-catch** (ký hiệu `|`) và biết điều kiện không chồng kiểu.
5. Viết kiểm thử hành vi, tránh `catch (Exception)` quá rộng và hiểu vai trò của `finally`.

## 2. Cơ chế chọn catch theo JLS

Theo [Java Language Specification 17, mục 14.20.1](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.1), nếu thân `try` ném một đối tượng ngoại lệ, Java tìm **catch đầu tiên có kiểu bắt tương thích với kiểu runtime của đối tượng bị ném**. Sau đó:

- Đối tượng ngoại lệ được gán cho tham số của catch đã chọn.
- Chỉ **khối handler được chọn** được thực thi; các `catch` còn lại của cùng cấu trúc không được chạy.
- Nếu handler hoàn thành bình thường, toàn cấu trúc `try/catch` cũng có thể hoàn thành bình thường, rồi điều khiển tiếp tục sau cấu trúc ấy.
- Nếu không có nhánh phù hợp, ngoại lệ tiếp tục lan ra ngoài; một `finally` liên quan vẫn được xét theo quy tắc của nó.
- Nếu handler lại ném exception, exception mới có thể lan tiếp; các sibling catch phía dưới **không tự bắt** lỗi mới ném từ handler phía trên.

Trong trường hợp có `finally`, xem thêm JLS mục 14.20.2: thông thường `finally` chạy sau thân `try` hoặc sau handler, kể cả khi rời phạm vi vì ngoại lệ. Nếu chính `finally` kết thúc bất thường, lý do chuyển điều khiển có thể thay đổi.

### 2.1. “Phù hợp” nghĩa là gì?

Ví dụ thứ bậc:

```text
java.lang.Throwable
├── java.lang.Error
└── java.lang.Exception
    ├── java.io.IOException
    │   └── java.io.FileNotFoundException
    └── java.lang.RuntimeException
        ├── java.lang.IllegalArgumentException
        │   └── java.lang.NumberFormatException
        └── java.lang.IllegalStateException
```

Một `FileNotFoundException` có thể được bắt bởi tham số kiểu `FileNotFoundException`, `IOException`, `Exception` hoặc `Throwable`, vì đó đều là các kiểu cha phù hợp. Một `NumberFormatException` có thể được bắt bởi `IllegalArgumentException` hoặc `RuntimeException`. Tuy nhiên `IOException` không có quan hệ cha–con với `IllegalArgumentException`, nên hai catch ấy **không có xung đột thứ tự chỉ do kế thừa**.

**Đừng nhầm**: Java không tự chọn “kiểu cụ thể nhất trong tất cả catch” rồi bỏ qua thứ tự mã nguồn. Chính vì tìm **catch đầu tiên phù hợp**, nếu catch cha đặt trước, catch con phía sau sẽ không thể có cơ hội nhận ngoại lệ con. Compiler phát hiện trường hợp này và từ chối mã.

### 2.2. Thứ tự là một ràng buộc biên dịch

**Hợp lệ:**

```java
try {
    openFile();
} catch (java.io.FileNotFoundException ex) {
    System.out.println("File does not exist");
} catch (java.io.IOException ex) {
    System.out.println("Other I/O failure");
}
```

Đây là đoạn minh họa trong phương thức; giả định `openFile()` được định nghĩa với khả năng ném `IOException`.

**Không hợp lệ (cố ý để nhận diện lỗi compiler):**

```java
try {
    openFile();
} catch (java.io.IOException ex) {
    System.out.println("I/O failure");
} catch (java.io.FileNotFoundException ex) {
    System.out.println("Missing file"); // Unreachable catch block.
}
```

Lý do: catch đầu đã bao phủ cả `IOException` và mọi lớp con, trong đó có `FileNotFoundException`. Catch thứ hai không thể nhận bất cứ trường hợp nào mà catch đầu không nhận; đây là **lỗi biên dịch**, không phải nhánh chạy nhưng luôn trả về false ở runtime.

Các quan hệ tương tự:

- `catch (NumberFormatException)` phải nằm trước `catch (IllegalArgumentException)` nếu có cả hai.
- `catch (IllegalStateException)` phải nằm trước `catch (RuntimeException)` nếu có cả hai.
- `catch (RuntimeException)` phải đứng trước `catch (Exception)` nếu cùng cấu trúc.
- `catch (Exception)` đứng trước `catch (IOException)` sẽ làm catch IOException bị che hoàn toàn.
- `catch (Exception)` không bắt `Error`, vì Error là nhánh khác nằm trực tiếp dưới Throwable.

## 3. Chương trình Java 17 đầy đủ: nhiều catch, thứ tự và multi-catch

Tạo tệp **`CatchOrderDemo.java`**. Chương trình dùng các exception chuẩn có quan hệ kế thừa rõ ràng, in ra chính xác handler được chọn, đồng thời chạy một ví dụ multi-catch ở phần cuối.

```java
import java.io.FileNotFoundException;
import java.io.IOException;

public class CatchOrderDemo {
    private static void process(String action) throws IOException {
        switch (action) {
            case "missing" -> throw new FileNotFoundException("data.csv");
            case "io" -> throw new IOException("read failed");
            case "bad-number" -> throw new NumberFormatException("invalid integer");
            case "bad-state" -> throw new IllegalStateException("service unavailable");
            case "ok" -> System.out.println("processed ok");
            default -> throw new IllegalArgumentException("unsupported action");
        }
    }

    private static void runOne(String action) {
        System.out.println("== " + action + " ==");
        try {
            process(action);
            System.out.println("try completed");
        } catch (FileNotFoundException ex) {
            System.out.println("file handler: " + ex.getMessage());
        } catch (IOException ex) {
            System.out.println("io handler: " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            System.out.println("argument handler: "
                    + ex.getClass().getSimpleName());
        } catch (RuntimeException ex) {
            System.out.println("runtime handler: " + ex.getMessage());
        } finally {
            System.out.println("finally");
        }
        System.out.println("after");
    }

    private static void runMultiCatch(String action) {
        System.out.println("== multi " + action + " ==");
        try {
            if (action.equals("file")) {
                throw new FileNotFoundException("missing.txt");
            }
            if (action.equals("number")) {
                throw new NumberFormatException("not a number");
            }
            System.out.println("normal");
        } catch (FileNotFoundException | NumberFormatException ex) {
            System.out.println("group handler: "
                    + ex.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        runOne("missing");
        runOne("io");
        runOne("bad-number");
        runOne("bad-state");
        runOne("ok");
        runOne("unknown");
        runMultiCatch("file");
        runMultiCatch("number");
        runMultiCatch("other");
    }
}
```

Biên dịch theo mức ngôn ngữ Java 17, sau đó chạy:

```bash
javac --release 17 -encoding UTF-8 CatchOrderDemo.java
java CatchOrderDemo
```

**Stdout chính xác** sau khi chạy chương trình:

```text
== missing ==
file handler: data.csv
finally
after
== io ==
io handler: read failed
finally
after
== bad-number ==
argument handler: NumberFormatException
finally
after
== bad-state ==
runtime handler: service unavailable
finally
after
== ok ==
processed ok
try completed
finally
after
== unknown ==
argument handler: IllegalArgumentException
finally
after
== multi file ==
group handler: FileNotFoundException
== multi number ==
group handler: NumberFormatException
== multi other ==
normal
```

Đầu ra này đã được đối chiếu bằng `javac --release 17` và `java` trên JDK hỗ trợ Java 17. Các thông báo được cố ý tạo bằng chuỗi xác định trong mã, không phụ thuộc ngôn ngữ thông điệp I/O của hệ điều hành.

## 4. Đọc chương trình theo từng exception

### 4.1. Tình huống `missing`: lớp con xuất hiện trước lớp cha

`process("missing")` ném `FileNotFoundException` với message `data.csv`. Dù cả catch `FileNotFoundException` và catch `IOException` có thể nhận kiểu ngoại lệ này, **catch đầu tiên** được chọn. Chương trình chỉ in `file handler: data.csv` rồi chạy `finally` và `after`.

Lợi ích nghiệp vụ: khi tệp thiếu, ta có thể gợi ý kiểm tra tên hoặc vị trí tệp, trong khi lỗi I/O khác cần xử lý riêng.

### 4.2. Tình huống `io`: handler tổng quát hơn nhận lỗi còn lại

`process("io")` ném đúng `IOException`, không phải `FileNotFoundException`. Catch đầu không phù hợp, nên Java chọn catch `IOException` kế tiếp.

Không cần viết `catch (FileNotFoundException)` cho mọi chức năng đọc tệp nếu nghiệp vụ không có cách xử lý riêng; khi cần phân biệt, thứ tự cha–con là bắt buộc.

### 4.3. Tình huống `bad-number`: nhánh unchecked vẫn tham gia cơ chế chọn

`NumberFormatException` kế thừa `IllegalArgumentException`, nên nó được bắt bởi `catch (IllegalArgumentException)` trong chương trình. Catch `RuntimeException` phía sau không chạy. Việc exception là unchecked chỉ ảnh hưởng yêu cầu `throws`/`catch` bắt buộc ở compile time, **không thay đổi quy tắc tìm handler theo kiểu runtime**.

Nếu cần thông báo nhập số cụ thể, có thể thêm `catch (NumberFormatException)` phía **trước** `catch (IllegalArgumentException)`.

### 4.4. Tình huống `bad-state`: handler chung của RuntimeException

`IllegalStateException` không phải nhánh của `IllegalArgumentException`, nên các catch cho I/O và đối số đều không phù hợp. Catch `RuntimeException` cuối cùng nhận lỗi. Chương trình in message `service unavailable`.

Điều này không mặc nhiên có nghĩa bắt mọi RuntimeException là một thiết kế tốt: ví dụ cố ý làm rõ thứ tự. Trong ứng dụng thật, `NullPointerException` do lỗi lập trình cũng là RuntimeException và có thể bị bắt quá rộng.

### 4.5. Tình huống `ok`: không có catch nào chạy

`process("ok")` in `processed ok` rồi trả về bình thường. Câu lệnh sau lời gọi trong try in `try completed`. Không có exception nên các nhánh catch đều được bỏ qua; `finally` vẫn chạy, rồi tới `after`.

### 4.6. Tình huống `unknown`: không phải mọi IllegalArgumentException đều là NumberFormatException

Giá trị không hỗ trợ làm nhánh `default` ném `IllegalArgumentException`. Catch tương ứng nhận trực tiếp, và chương trình in tên class `IllegalArgumentException`. Điều này giúp thấy catch một lớp cha có thể bao phủ nhiều lớp con nhưng không cần khai báo catch cho từng lớp con khi cách xử lý giống nhau.

### 4.7. `multi file` và `multi number`: một block xử lý nhiều kiểu

Khối:

```java
catch (FileNotFoundException | NumberFormatException ex) {
    System.out.println("group handler: "
            + ex.getClass().getSimpleName());
}
```

dùng cú pháp *multi-catch* giới thiệu trong Java 7. Hai kiểu trong dấu `|` không thuộc quan hệ cha–con. Khi một trong hai exception được ném, Java chạy **cùng một handler**; `ex.getClass().getSimpleName()` vẫn cho biết loại runtime cụ thể.

`runMultiCatch("other")` không ném exception, nên chỉ in `normal`.

## 5. Quan hệ kế thừa quyết định thứ tự hợp lệ

### 5.1. Không đặt catch cha trước catch con

Ví dụ cố ý **không biên dịch**:

```java
try {
    riskyIo();
} catch (IOException ex) {
    recoverIo();
} catch (FileNotFoundException ex) {
    recoverMissingFile();
}
```

Các hàm `riskyIo()`, `recoverIo()`, `recoverMissingFile()` là ký hiệu minh họa, không phải phương thức được cung cấp để chạy. Compiler không cần biết thực tế lúc chạy tệp có tồn tại hay không: chỉ xét việc catch đầu bao phủ mọi instance của lớp con. Vì thế mã sẽ lỗi **ngay khi biên dịch** do nhánh `FileNotFoundException` bị bắt hết bởi catch `IOException`.

Bản đúng:

```java
try {
    riskyIo();
} catch (FileNotFoundException ex) {
    recoverMissingFile();
} catch (IOException ex) {
    recoverIo();
}
```

### 5.2. Hai kiểu không có quan hệ cha–con có thể đổi thứ tự

Nếu một phương thức có thể ném `IOException` (checked) và `IllegalArgumentException` (unchecked):

```java
try {
    riskyOperation();
} catch (IOException ex) {
    handleIo();
} catch (IllegalArgumentException ex) {
    handleBadInput();
}
```

Hoặc đổi vị trí hai catch trên đều không tạo lỗi **do che khuất quan hệ kế thừa**, vì một IOException không phải IllegalArgumentException và ngược lại. Đây là đoạn minh họa chưa độc lập (các hàm tượng trưng); riêng điều kiện **checked exception có thể phát sinh từ try** vẫn cần thỏa.

Vì thế cách nói chính xác là: **với các kiểu có quan hệ bao hàm, đặt kiểu hẹp trước kiểu rộng**, thay vì ép tất cả exception của mọi package vào một bảng “mức độ cụ thể” chung không có nghĩa.

### 5.3. Kiểu runtime quyết định handler, còn compiler kiểm tra tính hợp lệ của mã

Nếu lời gọi được khai báo `throws IOException`, đối tượng thực tế có thể là `FileNotFoundException`. Compiler nhìn chữ ký khi kiểm tra checked exception; khi chạy, JVM so kiểu **instance bị ném** với các catch theo thứ tự.

Đừng suy rằng một method khai báo `throws Exception` thì chỉ chạy `catch (Exception)`, cũng đừng suy rằng một method không khai báo `throws` thì không thể ném `RuntimeException`.

## 6. Multi-catch không phải nhiều catch nối tiếp

Java có hai cơ chế khác nhau:

**Nhiều catch nối tiếp:** mỗi loại có một khối code riêng, phù hợp nếu phản ứng khác nhau.

```java
try {
    readAndParse();
} catch (IOException ex) {
    reportReadFailure(ex);
} catch (NumberFormatException ex) {
    reportInvalidNumber(ex);
}
```

**Một multi-catch:** nhóm các exception có chung cách xử lý.

```java
try {
    readAndParse();
} catch (IOException | NumberFormatException ex) {
    reportFailure(ex);
}
```

Hai đoạn trên chỉ minh họa ý tưởng thiết kế; `readAndParse`, `reportReadFailure`, `reportInvalidNumber` và `reportFailure` không được định nghĩa ở đây.

### 6.1. Các alternative trong cùng multi-catch không được chồng kiểu

Sai, **cố ý không thể biên dịch**:

```java
catch (FileNotFoundException | IOException ex) {
    // Sai: FileNotFoundException là subtype của IOException.
}
```

Đúng:

```java
catch (FileNotFoundException | NumberFormatException ex) {
    // Hai nhánh không có quan hệ kế thừa trực tiếp.
}
```

Java từ chối tổ hợp `FileNotFoundException | IOException` vì một alternative là subtype của alternative khác. Nếu hai kiểu liên quan cha–con và cùng được xử lý giống nhau, chỉ cần catch **kiểu cha phù hợp**, ví dụ `catch (IOException ex)`. Nếu cần xử lý khác nhau, viết nhiều catch riêng đúng thứ tự.

### 6.2. Tham số multi-catch được coi là final

Trong:

```java
catch (FileNotFoundException | NumberFormatException ex) {
    System.out.println(ex.getMessage());
    // ex = new RuntimeException(); // Không hợp lệ.
}
```

tham số `ex` là *implicitly final* nên không được gán lại. Khi cần phân biệt hành vi theo loại, thường nên dùng các catch riêng để tránh kiểm tra `instanceof` dày đặc trong một catch nhóm.

Kiểu của tham số multi-catch còn được suy luận từ các alternative (để gọi phương thức chung an toàn); không thể giả định nó luôn có API riêng của từng alternative. Các phương thức chung của `Throwable` như `getMessage()` và `getCause()` luôn dùng được.

### 6.3. Có thể kết hợp multi-catch và catch tổng quát

```java
try {
    operation();
} catch (FileNotFoundException | NumberFormatException ex) {
    handleKnownCase(ex);
} catch (IOException ex) {
    handleOtherIo(ex);
}
```

Trong ví dụ tượng trưng trên, nếu `operation()` có thể ném `IOException`, thứ tự hợp lệ: nhánh đầu bắt FileNotFoundException hoặc NumberFormatException; nhánh sau bắt các IOException còn lại. Một `FileNotFoundException` sẽ vào nhánh đầu, không tiếp tục xuống nhánh sau.

## 7. Catch rộng: dùng khi nào và rủi ro gì?

### 7.1. catch Exception

`catch (Exception ex)` bắt `IOException`, `IllegalArgumentException`, `NullPointerException` và rất nhiều lỗi khác dưới `Exception`. Nó không bắt các `Error` như `OutOfMemoryError`, vì `Error` không kế thừa Exception.

Dùng `catch (Exception)` ở lớp biên ứng dụng (ví dụ job runner) đôi khi phù hợp để gom lỗi, ghi thông tin và báo trạng thái cuối cùng, nhưng phải có **chính sách rõ**. Một catch rộng có thể vô tình nuốt lỗi lập trình và biến thất bại nghiêm trọng thành dữ liệu trả về bình thường.

Trong logic nghiệp vụ, ưu tiên catch những loại mà tầng hiện tại **biết cách phục hồi**, ví dụ parse sai dữ liệu từng dòng hoặc tệp chưa có. Nếu không có cách khôi phục hợp lệ, cho exception lan tới tầng xử lý thích hợp.

### 7.2. catch RuntimeException và Error

`catch (RuntimeException)` sẽ bao phủ rất nhiều lỗi do code như `NullPointerException`, `IndexOutOfBoundsException` và `IllegalStateException`. Khi handler này đứng trước `catch (IllegalArgumentException)` hoặc `catch (IllegalStateException)`, compiler báo handler cụ thể ở sau không thể tới được.

`catch (Throwable)` còn rộng hơn nữa vì bao gồm cả `Error`. Phần lớn chương trình không nên bắt `Throwable` chỉ để cố chạy tiếp, vì nhiều Error phản ánh vấn đề mà ứng dụng không thể khôi phục an toàn. Có thể có trường hợp framework cấp thấp đặc biệt, nhưng đó là quyết định thiết kế được kiểm soát.

### 7.3. Giữ cause và stack trace khi chuyển tầng

Nếu tầng xử lý cần tạo exception nghiệp vụ có ý nghĩa hơn, hãy bảo toàn nguyên nhân:

```java
try {
    readConfiguration();
} catch (IOException ex) {
    throw new IllegalStateException("Cannot load configuration", ex);
}
```

Đây là mã minh họa trong một phương thức có `readConfiguration()` phù hợp. Constructor `IllegalStateException(String, Throwable)` giữ nguyên `IOException` làm cause. Nếu chỉ viết `throw new IllegalStateException("failed");`, ta làm mất chuỗi nguyên nhân gốc trừ khi đã lưu riêng.

Nếu chỉ muốn cho cùng exception tiếp tục lan truyền, có thể dùng `throw ex;` (rethrow), với yêu cầu checked exception ở chữ ký hoặc caller tương ứng. Việc xử lý trong một catch không khiến các sibling catch của cùng try có cơ hội chạy tiếp.

## 8. catch và finally: thứ tự nhìn thấy trên stdout

Trong ví dụ chính, mỗi lần gọi `runOne` đều in `finally` rồi `after`, cả khi có exception lẫn khi thao tác thành công.

Với tình huống không có exception:

```text
try hoàn thành → bỏ qua tất cả catch → finally → câu lệnh sau try/catch/finally
```

Khi exception có handler tương ứng:

```text
throw → catch đầu tiên phù hợp → finally → câu lệnh sau try/catch/finally
```

Khi exception không được bắt tại cấu trúc hiện tại:

```text
throw → không có catch phù hợp → finally → tiếp tục lan lên caller
```

Trong trường hợp cuối, **câu lệnh nằm ngay sau cấu trúc try ở cùng đường thực thi sẽ không chạy bình thường**. Nếu `finally` tự ném exception khác, exception đó có thể thay thế exception đang lan truyền; không đặt `return` hay `throw` trong finally một cách tùy tiện.

**Một lưu ý khác:** `try-with-resources` đóng tài nguyên theo quy tắc riêng trước khi chuyển sang catch/finally bên ngoài cấu trúc mở rộng. Lỗi từ `close()` có thể trở thành suppressed exception; không vì thế mà bỏ qua thứ tự catch.

## 9. Những ràng buộc compiler ngoài thứ tự cha–con

Thứ tự không phải điều kiện biên dịch duy nhất.

### 9.1. Checked exception được catch phải có cơ sở phát sinh

Ví dụ **không hợp lệ**:

```java
try {
    int number = Integer.parseInt("123");
} catch (IOException ex) {
    System.out.println("I/O failed");
}
```

Compiler có thể chứng minh đoạn try này không ném checked `IOException` hoặc kiểu liên quan, nên catch IOException bị từ chối (theo quy tắc exception analysis). `NumberFormatException` thì khác: nó là unchecked, do đó có thể được bắt mà không cần một `throws` tương ứng.

Ở đây đừng lạm dụng cú pháp `catch IOException` “cho chắc”: hãy bắt loại mà mã có thể phát sinh, hoặc thiết kế cách truyền lỗi rõ ràng ở API.

### 9.2. Không được lặp catch có cùng kiểu

Đoạn sau cũng không thể biên dịch:

```java
try {
    possiblyFail();
} catch (IllegalArgumentException ex) {
    handleA(ex);
} catch (IllegalArgumentException ex) {
    handleB(ex);
}
```

Một catch đầu đã bao phủ toàn bộ những exception có thể vào catch thứ hai. Mã chỉ là minh họa, các method chưa được định nghĩa.

### 9.3. Đừng nhầm hai mức “không thể tới được”

- **Nhánh bị che bởi catch trước**: do kiểu cha đã bắt toàn bộ kiểu con, Java từ chối ở compile time.
- **Nhánh hợp lệ nhưng dữ liệu test hiện tại không kích hoạt**: vẫn là handler có thể dùng trong một lần chạy khác, nên không lỗi chỉ vì test hôm nay không gây ra ngoại lệ đó.

Ví dụ `catch (FileNotFoundException)` đứng trước `catch (IOException)` hoàn toàn hợp lệ khi thao tác có thể ném cả IOException chung. Trong lần chạy ném FileNotFoundException, catch IOException không chạy nhưng **không** bị coi là vô hiệu về ngôn ngữ.

## 10. Mẫu thiết kế và quy trình kiểm tra thực tế

Khi xây dựng nhiều catch cho một thao tác:

1. **Liệt kê lỗi có thể phát sinh**: xem `throws` của API cho checked exceptions; tìm những unchecked exception có ý nghĩa nghiệp vụ.
2. **Vẽ quan hệ kế thừa** giữa các kiểu dự kiến bắt; dùng Javadoc hoặc IDE để xác nhận kiểu cha–con.
3. **Chọn chính sách phục hồi riêng**: ví dụ tệp thiếu → thông báo đường dẫn; lỗi I/O khác → báo lỗi đọc; dữ liệu sai → yêu cầu sửa.
4. **Sắp xếp các handler có quan hệ kế thừa** từ subtype đến supertype.
5. **Gom thành multi-catch** nếu các kiểu không nằm trong quan hệ subtype và thực sự có cùng cách xử lý.
6. **Chọn ranh giới cleanup** bằng `try-with-resources` hoặc `finally` phù hợp.
7. **Biên dịch và test từng nhánh**: một input gây lỗi cụ thể, một input gây lỗi cha, một input thành công; kiểm tra chỉ đúng handler được chọn.
8. **Test đường lan truyền** khi không có catch phù hợp; xác nhận exception không bị nuốt và trạng thái sau lỗi nhất quán.

Bảng quyết định với ví dụ trong bài:

| Exception runtime | Catch đầu tiên phù hợp | Vì sao |
| --- | --- | --- |
| `FileNotFoundException` | `FileNotFoundException` | Nhánh cụ thể đầu tiên |
| `IOException` | `IOException` | Không phải FileNotFoundException |
| `NumberFormatException` | `IllegalArgumentException` | NumberFormatException là subtype |
| `IllegalStateException` | `RuntimeException` | Không thuộc nhánh IllegalArgumentException |
| `IllegalArgumentException` | `IllegalArgumentException` | Kiểu phù hợp trực tiếp |
| Không có exception | Không catch nào | Try hoàn thành bình thường |

Có thể xác nhận một cách tự động bằng JUnit/AssertJ nếu dự án có sẵn. Với ví dụ console nhỏ, kiểm tra stdout và so kiểu exception đã đủ thể hiện quy tắc, không cần phụ thuộc framework.

## 11. Lỗi phổ biến và cách sửa

- **Bắt cha trước con.** Sửa thứ tự `FileNotFoundException` trước `IOException`, `NumberFormatException` trước `IllegalArgumentException`.
- **Hiểu nhầm Java chọn “catch cụ thể nhất” bất kể vị trí.** Java xét theo thứ tự xuất hiện; compiler cấm trường hợp bị che hoàn toàn.
- **Tưởng nhiều catch có thể cùng chạy trên một exception.** Chỉ một catch của cùng cấu trúc được chọn.
- **Đưa hai kiểu cha–con vào cùng multi-catch.** `IOException | FileNotFoundException` không hợp lệ; dùng IOException đơn lẻ hoặc nhiều handler.
- **Dùng `catch (Exception)` để che mọi lỗi.** Dễ nuốt bug lập trình; chỉ dùng ở ranh giới có chính sách xử lý rõ.
- **Không phân biệt checked và unchecked.** Checked exception có phân tích compiler riêng; RuntimeException vẫn có thể được bắt và tuân thứ tự như thường.
- **Giả định `catch (Exception)` bao gồm `Error`.** Không; Error là nhánh khác dưới Throwable.
- **Catch một IOException mà try không thể ném.** Có thể lỗi biên dịch; đọc hợp đồng API trước.
- **Mất `cause` khi bọc exception.** Tạo exception mới với constructor nhận cause nếu cần giữ lỗi gốc.
- **Nghĩ handler tự động thử lại thao tác lỗi.** Catch chỉ chuyển luồng điều khiển; retry cần code riêng và cân nhắc side effects.
- **Quên `finally`/resource cleanup.** Sau handler có thể vẫn cần đóng tài nguyên; `try-with-resources` thường là lựa chọn tốt cho `AutoCloseable`.
- **So sánh toàn bộ thông điệp exception của hệ điều hành.** Message thực tế có thể khác môi trường; kiểm tra kiểu exception, chính sách và các chuỗi do ứng dụng định nghĩa.

## 12. Tự kiểm tra kiến thức

**Câu 1.** Với một `FileNotFoundException`, nếu viết `catch (IOException)` trước `catch (FileNotFoundException)`, handler nào chạy?

*Đáp án:* Chương trình **không biên dịch** vì handler cụ thể phía sau bị che. Không có chuyện Java chạy rồi tùy runtime chọn một trong hai.

**Câu 2.** Nếu một `NumberFormatException` xuất hiện nhưng chỉ có `catch (IllegalArgumentException)`, nhánh ấy có bắt được không?

*Đáp án:* Có. NumberFormatException kế thừa IllegalArgumentException.

**Câu 3.** `IOException` và `IllegalArgumentException` có phải luôn sắp theo một “thứ tự kế thừa” bắt buộc không?

*Đáp án:* Không. Hai kiểu không có quan hệ cha–con; thứ tự giữa chúng có thể chọn theo cách tổ chức code và chính sách nghiệp vụ, miễn các ràng buộc compiler khác hợp lệ.

**Câu 4.** Khi handler đã bắt và hoàn thành bình thường, catch khác của cùng try có chạy tiếp không?

*Đáp án:* Không. Java chọn catch phù hợp đầu tiên, thực hiện handler đó rồi chuyển sang finally (nếu có) và phần sau cấu trúc.

**Câu 5.** Vì sao `catch (IOException | FileNotFoundException ex)` sai?

*Đáp án:* Một alternative là subtype của alternative khác. Nếu xử lý giống nhau, dùng một catch IOException; nếu khác, tách thành các catch theo thứ tự con trước cha.

**Câu 6.** Nếu catch được chọn lại ném một `IllegalStateException`, catch `RuntimeException` phía dưới của **cùng try** có bắt được không?

*Đáp án:* Không. Sibling catch chỉ tham gia chọn handler cho ngoại lệ từ **thân try**, không xử lý exception mới phát sinh trong sibling catch. Một try/catch bao ngoài mới có thể bắt.

**Câu 7.** Nếu try hoàn thành bình thường, finally có được chạy không?

*Đáp án:* Có, trong hoạt động bình thường của Java, finally vẫn được thực hiện theo quy tắc của cấu trúc. Các catch được bỏ qua.

## 13. Tài liệu đối chiếu chính thức

- [JLS Java SE 17 §14.20 – The try Statement](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20): cú pháp nhiều catch, multi-catch và finally.
- [JLS Java SE 17 §14.20.1 – Execution of try-catch](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.1): quy tắc **chọn catch phù hợp đầu tiên** theo kiểu runtime.
- [JLS Java SE 17 §14.20.2 – Execution of try-catch-finally](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.2): luồng thực thi sau catch và finally.
- [JLS Java SE 17 §11.2.3 – Exception Checking](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.2.3): checked exception có cơ sở phát sinh và lỗi khi catch bị che.
- [Java 17 API – FileNotFoundException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/FileNotFoundException.html) và [IOException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/IOException.html): quan hệ kế thừa của exception I/O.
- [Java 17 API – NumberFormatException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/NumberFormatException.html) và [IllegalArgumentException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/IllegalArgumentException.html): quan hệ kế thừa unchecked exception.

**Ghi nhớ:** Trong một `try` có nhiều `catch`, hãy bắt đầu bằng các ngoại lệ hẹp mà bạn cần xử lý riêng, sau đó mới đặt các kiểu cha rộng hơn. Java chọn **handler phù hợp đầu tiên**, multi-catch chỉ gom những kiểu không nằm trong quan hệ subtype với nhau, và compiler giúp phát hiện các nhánh bị che ngay trước lúc chạy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="try-catch-004">Khối catch đầu tiên phù hợp được thực thi</a>

<details>
<summary>Xem chi tiết</summary>

# Khối `catch` đầu tiên phù hợp được thực thi trong Java

Khi một ngoại lệ phát sinh trong khối `try`, Java tìm một nhánh `catch` có thể nhận **đối tượng ngoại lệ thực tế**. Với các `catch` thuộc cùng một `try`, Java xét chúng **từ trên xuống dưới theo thứ tự khai báo** và chỉ thực thi **nhánh đầu tiên có kiểu tương thích**. Khi đã chọn được nhánh này, Java không chạy tiếp những `catch` còn lại của cùng cấu trúc, ngay cả khi kiểu của chúng cũng có thể bắt được đối tượng ấy.

Đây là quy tắc của Java Language Specification (JLS) §14.20.1. Nắm vững nó giúp viết đúng thứ tự bắt ngoại lệ cha/con, dự đoán luồng xử lý, nhận ra mã `catch` không thể tới, và tránh nhầm một ngoại lệ được ném lại từ `catch` với ngoại lệ mới được xử lý bởi nhánh kế bên.

## 1. Nhìn ngay vào quy tắc chọn nhánh

```java
try {
    throw new NumberFormatException("not a number");
} catch (NumberFormatException ex) {
    System.out.println("specific");
} catch (IllegalArgumentException ex) {
    System.out.println("general");
}
```

Đây là đoạn mã hợp lệ khi đặt trong một phương thức. Kết quả duy nhất là:

```text
specific
```

`NumberFormatException` kế thừa `IllegalArgumentException`, nên về mặt quan hệ kiểu **cả hai `catch` đều có thể tiếp nhận** ngoại lệ này. Tuy nhiên, nhánh `catch (NumberFormatException ex)` xuất hiện đầu tiên nên được chọn. `catch (IllegalArgumentException ex)` không chạy trong lần ném này.

Một cách diễn đạt chính xác:

1. Đánh giá và thực thi phần `try`.
2. Nếu `try` kết thúc bình thường, **không `catch` nào chạy**.
3. Nếu `try` hoàn tất đột ngột vì ném đối tượng ngoại lệ `V`, xét các `catch` của chính cấu trúc `try` này **theo thứ tự trái sang phải trong đặc tả**, tương ứng thứ tự từ trên xuống dưới trong mã nguồn.
4. Chọn `catch` **đầu tiên** có kiểu bắt được tương thích với **kiểu thực tế tại runtime** của `V`, rồi gán `V` cho tham số `catch`.
5. Nếu khối `catch` được chọn xử lý và hoàn tất bình thường, toàn bộ `try-catch` có thể hoàn tất bình thường. Nếu nó lại `throw` hay `return`, cấu trúc bao ngoài tiếp tục theo lý do hoàn tất mới đó.
6. Nếu không có `catch` phù hợp, ngoại lệ **tiếp tục lan truyền** ra ngữ cảnh bên ngoài.

**Một lần ném ngoại lệ không khiến tất cả các nhánh `catch` tương thích cùng chạy.** Các `catch` là những phương án xử lý thay thế nhau, không phải các bộ xử lý liên tiếp.

## 2. Kiểu thực tế, quan hệ kế thừa và tính tương thích

Ngoại lệ được ném phải là một đối tượng thuộc `Throwable` hoặc lớp con. Để một `catch (T ex)` thông thường bắt được đối tượng `V`, kiểu runtime của `V` phải gán được cho kiểu `T`. Một nhánh dùng kiểu cha có thể bắt một đối tượng kiểu con.

Ví dụ sơ đồ kế thừa tiêu biểu:

```text
Throwable
├── Error
│   └── OutOfMemoryError
└── Exception
    ├── IOException
    │   ├── FileNotFoundException
    │   └── EOFException
    └── RuntimeException
        └── IllegalArgumentException
            └── NumberFormatException
```

Cây này chỉ thể hiện những lớp phục vụ bài học. Từ cây trên:

| Đối tượng đang được ném | `catch` có thể tiếp nhận | `catch` không phù hợp |
| --- | --- | --- |
| `new NumberFormatException(...)` | `NumberFormatException`, `IllegalArgumentException`, `RuntimeException`, `Exception`, `Throwable` | `IOException` |
| `new FileNotFoundException(...)` | `FileNotFoundException`, `IOException`, `Exception`, `Throwable` | `RuntimeException` |
| `new EOFException(...)` | `EOFException`, `IOException`, `Exception`, `Throwable` | `FileNotFoundException` |
| `new OutOfMemoryError(...)` | `OutOfMemoryError`, `Error`, `Throwable` | `Exception` |

Lưu ý: bảng mô tả **khả năng tương thích kiểu**, không phải lời khuyên bắt `OutOfMemoryError` trong ứng dụng. Lỗi thuộc nhánh `Error` thường cần để lan truyền và điều tra ở cấp phù hợp.

Nếu trong `try` có:

```java
IOException failure = new FileNotFoundException("settings.ini");
throw failure;
```

thì đối tượng được ném thực sự là `FileNotFoundException`. Bất kể biến `failure` được khai báo kiểu `IOException`, việc chọn `catch` lúc chạy dựa vào **lớp thực tế của đối tượng**, không chỉ dựa vào kiểu khai báo của biến. Trình biên dịch vẫn dùng thông tin kiểu khai báo và phân tích ngoại lệ để kiểm tra những nhánh `catch` nào hợp lệ trong mã.

## 3. Phải đặt ngoại lệ cụ thể trước ngoại lệ tổng quát

Trong cùng một chuỗi `catch`, nếu kiểu của một nhánh là lớp con của kiểu ở nhánh khác, đặt nhánh **con trước cha**. Nhánh cha đặt trước sẽ bắt luôn các đối tượng kiểu con, khiến nhánh con sau đó không bao giờ được chọn.

**Đúng:**

```java
try {
    readConfig();
} catch (FileNotFoundException ex) {
    System.out.println("Không tìm thấy tệp");
} catch (IOException ex) {
    System.out.println("Lỗi I/O khác");
}
```

Đây là đoạn minh họa bên trong phương thức, giả sử `readConfig()` được khai báo `throws IOException` và `FileNotFoundException` đã được import từ `java.io`. Nhánh đầu xử lý trường hợp chuyên biệt; nhánh sau là dự phòng cho những `IOException` còn lại.

**Sai — không biên dịch:**

```java
try {
    readConfig();
} catch (IOException ex) {
    System.out.println("I/O");
} catch (FileNotFoundException ex) {
    System.out.println("Không tìm thấy tệp");
}
```

Ở đoạn sai, `IOException` nằm trước và là lớp cha của `FileNotFoundException`, nên mọi `FileNotFoundException` đều bị nhánh đầu bắt. Trình biên dịch Java bác bỏ nhánh sau với chẩn đoán kiểu **exception FileNotFoundException has already been caught**. Đây là lỗi biên dịch, không phải một nhánh chỉ đơn giản bị bỏ qua khi chạy.

Quy tắc áp dụng tương tự cho:

- `catch (Exception ex)` đặt trước `catch (IOException ex)`.
- `catch (RuntimeException ex)` đặt trước `catch (IllegalArgumentException ex)`.
- `catch (IllegalArgumentException ex)` đặt trước `catch (NumberFormatException ex)`.
- `catch (Throwable ex)` đặt trước bất kỳ kiểu `Throwable` con nào.

Thứ tự **không bắt buộc phải chuyên biệt hơn** giữa hai lớp *không có quan hệ cha/con*, ví dụ `IOException` và `SQLException`; trong trường hợp đó, mỗi nhánh bắt các kiểu khác nhau. Tuy nhiên, nên bố trí chúng theo thứ tự dễ đọc và ý nghĩa nghiệp vụ.

## 4. Chương trình Java 17+: kiểm chứng `catch` đầu tiên phù hợp

Lưu thành `CatchOrderDemo.java`:

```java
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;

public class CatchOrderDemo {
    static void fetch(int code) throws IOException {
        switch (code) {
            case 0 -> throw new FileNotFoundException("profile.txt");
            case 1 -> throw new EOFException("partial data");
            case 2 -> throw new IllegalArgumentException("invalid code");
            default -> System.out.println("fetch=OK");
        }
    }

    public static void main(String[] args) {
        for (int code = 0; code < 4; code++) {
            try {
                fetch(code);
            } catch (FileNotFoundException ex) {
                System.out.println("file=" + ex.getMessage());
            } catch (IOException ex) {
                System.out.println("io=" + ex.getClass().getSimpleName());
            } catch (RuntimeException ex) {
                System.out.println("runtime=" + ex.getMessage());
            }

            System.out.println("done=" + code);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 CatchOrderDemo.java
java CatchOrderDemo
```

Kết quả:

```text
file=profile.txt
done=0
io=EOFException
done=1
runtime=invalid code
done=2
fetch=OK
done=3
```

Diễn giải từng lượt:

1. `code = 0`: `fetch()` ném `FileNotFoundException`. Nhánh `catch (FileNotFoundException)` được chọn đầu tiên dù `IOException` cũng tương thích.
2. `code = 1`: `fetch()` ném `EOFException`, vốn là lớp con của `IOException` nhưng không phải `FileNotFoundException`. Nhánh đầu không phù hợp, nhánh `IOException` chạy.
3. `code = 2`: `IllegalArgumentException` thuộc `RuntimeException`; hai nhánh I/O không phù hợp nên nhánh `RuntimeException` chạy. Việc `fetch()` chỉ khai báo `throws IOException` không ngăn nó ném unchecked exception.
4. `code = 3`: không có ngoại lệ, chỉ in `fetch=OK`; toàn bộ ba `catch` được bỏ qua.

Dòng `done=...` xuất hiện sau mọi lượt vì mỗi `catch` đã chọn hoàn tất bình thường. Nếu một `catch` ném lại lỗi hoặc có ngoại lệ khác chưa được xử lý, dòng đó không nhất thiết chạy.

## 5. Không có ngoại lệ thì không chạy `catch` nào

`catch` không hoạt động như nhánh `else` bắt buộc sau mỗi `try`. Nếu toàn bộ `try` hoàn tất bình thường, Java bỏ qua các `catch`:

```java
try {
    System.out.println("try complete");
} catch (RuntimeException ex) {
    System.out.println("will not print in this run");
}
System.out.println("after");
```

Đây là đoạn minh họa bên trong phương thức, cho kết quả:

```text
try complete
after
```

`catch (RuntimeException ex)` hợp lệ dù khối `try` không chứa lời gọi ném unchecked exception tường minh. Nhưng không có lỗi runtime nào xảy ra trong lần thực thi này nên nó không chạy.

Nếu đặt `catch (IOException ex)` cho một `try` **không có khả năng ném `IOException` checked** (và không có tài nguyên có thể ném lỗi đó khi đóng), trình biên dịch có thể bác bỏ với thông báo `exception IOException is never thrown in body of corresponding try statement`. Việc `catch` có hợp lệ lúc biên dịch và việc nó có được chọn ở runtime là hai câu hỏi khác nhau.

## 6. Một `catch` đã chọn không “chạy tiếp” các `catch` phía dưới

Xét đoạn mã:

```java
try {
    throw new IllegalArgumentException("bad");
} catch (IllegalArgumentException ex) {
    System.out.println("first");
} catch (RuntimeException ex) {
    System.out.println("second");
} catch (Exception ex) {
    System.out.println("third");
}
System.out.println("after");
```

Kết quả:

```text
first
after
```

Ba nhánh đều có thể nhận đối tượng `IllegalArgumentException` theo quan hệ kế thừa, nhưng chỉ nhánh đầu chạy. Sau khi nhánh đó hoàn tất bình thường, luồng chuyển đến câu lệnh **sau toàn bộ `try-catch`**, không chuyển sang `catch` thứ hai.

Điều này khác với một số cấu trúc `switch` truyền thống có thể *fall through*. Các khối `catch` không có cơ chế fall-through; không cần viết `break` để dừng chuỗi `catch`.

Nếu muốn xử lý chung cho nhiều kiểu ngoại lệ, có thể viết **multi-catch** hoặc chuyển logic chung vào một phương thức, thay vì mong nhiều nhánh `catch` cùng chạy.

## 7. `catch` mới ném ngoại lệ: nhánh kế bên có bắt được không?

**Không.** Các `catch` của một `try` chỉ chọn nhánh để xử lý ngoại lệ từ phần `try` (bao gồm các quy tắc mở rộng với tài nguyên). Nếu một nhánh `catch` đã được chọn rồi **ném ngoại lệ mới**, các nhánh `catch` còn lại **cùng cấp** không được thử tiếp cho ngoại lệ mới đó.

Ví dụ:

```java
try {
    throw new IllegalArgumentException("input");
} catch (IllegalArgumentException ex) {
    throw new IllegalStateException("translated", ex);
} catch (IllegalStateException ex) {
    System.out.println("not selected for the new error");
}
```

Đây là **đoạn minh họa cú pháp hợp lệ** trong một phương thức, nhưng khi chạy ngoại lệ `IllegalStateException` được truyền ra ngoài cấu trúc `try-catch` nếu không có bộ xử lý ở tầng bao ngoài. Nhánh `catch (IllegalStateException)` chỉ là phương án cho lỗi được ném từ `try`, không xử lý lỗi mới của nhánh `catch` trước.

Trong thực tế, chuyển đổi ngoại lệ ở tầng xử lý có thể cần thiết; nên gắn `cause` để giữ nguyên nhân ban đầu, như constructor `new IllegalStateException("translated", ex)` ở trên.

## 8. Chương trình Java 17+: `catch` lồng nhau và ngoại lệ được chuyển đổi

Lưu thành `NestedCatchDemo.java`:

```java
import java.io.IOException;

public class NestedCatchDemo {
    static void process() {
        try {
            try {
                throw new IOException("disk");
            } catch (IOException ex) {
                System.out.println("inner=" + ex.getMessage());
                throw new IllegalStateException("service failed", ex);
            } catch (RuntimeException ex) {
                System.out.println("unreached sibling");
            } finally {
                System.out.println("inner-finally");
            }
        } catch (IllegalStateException ex) {
            System.out.println("outer=" + ex.getMessage());
            System.out.println("cause=" + ex.getCause().getClass().getSimpleName());
        }

        System.out.println("after");
    }

    public static void main(String[] args) {
        process();
    }
}
```

```bash
javac -encoding UTF-8 NestedCatchDemo.java
java NestedCatchDemo
```

Kết quả:

```text
inner=disk
inner-finally
outer=service failed
cause=IOException
after
```

Luồng xử lý:

1. Phần `try` bên trong ném `IOException`; nhánh `catch (IOException)` bên trong phù hợp và được thực thi.
2. Nhánh này tạo `IllegalStateException` mới, giữ `IOException` làm `cause`.
3. Nhánh `catch (RuntimeException)` kế bên **không** được chạy cho lỗi vừa ném trong nhánh `catch` đầu.
4. `finally` của cấu trúc bên trong vẫn chạy và hoàn tất bình thường.
5. Ngoại lệ `IllegalStateException` truyền ra cấu trúc `try` bên ngoài; `catch (IllegalStateException)` phía ngoài nhận nó.
6. Nhánh ngoài hoàn tất bình thường, nên `after` được in.

Ví dụ chứng minh hai quy tắc đồng thời: **chỉ một `catch` được chọn trong mỗi cấu trúc `try`**, và các cấu trúc `try-catch` lồng nhau có thể xử lý **những đối tượng ngoại lệ ở các thời điểm khác nhau**.

## 9. Tìm `catch` theo từng phạm vi `try`, không quét tất cả `catch` trong chương trình

Từ “đầu tiên” chỉ có nghĩa khi đã xác định **cấu trúc `try` đang xét**. Java tìm bộ xử lý gần nhất trên đường lan truyền của ngoại lệ. Các `catch` ở cùng một `try` được xét theo thứ tự khai báo; nếu không có nhánh phù hợp, ngoại lệ tiếp tục đi ra ngoài và có thể gặp `try-catch` ở phương thức gọi.

```text
caller(): try {
    service(): try {
        repository(): throw FileNotFoundException
    } catch (IOException) {
        // chọn ở service() nếu lỗi tới đây và catch phù hợp
    }
} catch (Exception) {
    // chỉ nhận nếu ngoại lệ đi qua service() mà chưa được xử lý
}
```

Đây là sơ đồ luồng gọi để giải thích, **không phải cú pháp Java**.

Một `catch (Exception)` trong `try` bao quanh gần hơn có thể được chọn trước một `catch (FileNotFoundException)` ở tầng gọi xa hơn. Không có thuật toán tìm “lớp bắt cụ thể nhất trong toàn ứng dụng”. Java ưu tiên **phạm vi xử lý** trước, rồi chọn **nhánh đầu tiên tương thích** bên trong phạm vi đó.

Tương tự, một đối tượng ngoại lệ có thể đi từ `repository()` lên `service()` rồi `controller()` nếu không có `catch` thích hợp ở tầng thấp. Mệnh đề `throws` mô tả nghĩa vụ checked exception lúc biên dịch; nó không thay đổi quy tắc lựa chọn nhánh `catch` lúc chạy.

## 10. `multi-catch` có nhiều kiểu nhưng chỉ một khối xử lý

Từ Java 7, cú pháp `catch (IOException | SQLException ex)` cho phép **một nhánh `catch` nhận một trong nhiều kiểu**. Về lựa chọn bộ xử lý, đây **vẫn là một nhánh `catch` duy nhất**. Không có chuyện Java thực thi riêng từng nhánh con vì thấy toán tử `|`.

Lưu thành `MultiCatchDemo.java`:

```java
import java.io.IOException;
import java.sql.SQLException;

public class MultiCatchDemo {
    static void work(int mode) throws IOException, SQLException {
        if (mode == 0) {
            throw new IOException("disk");
        }
        if (mode == 1) {
            throw new SQLException("database");
        }
        System.out.println("work=OK");
    }

    public static void main(String[] args) {
        for (int mode = 0; mode < 3; mode++) {
            try {
                work(mode);
            } catch (IOException | SQLException ex) {
                System.out.println("handled="
                        + ex.getClass().getSimpleName() + ":" + ex.getMessage());
            }
        }
    }
}
```

```bash
javac -encoding UTF-8 MultiCatchDemo.java
java MultiCatchDemo
```

Kết quả:

```text
handled=IOException:disk
handled=SQLException:database
work=OK
```

Một số giới hạn:

- Các kiểu trong cùng `multi-catch` không được có quan hệ lớp cha/con với nhau. Ví dụ `catch (FileNotFoundException | IOException ex)` **không hợp lệ**, vì `FileNotFoundException extends IOException`.
- Tham số `ex` trong `multi-catch` là **implicitly final**: không thể gán lại biến đó.
- Có thể gọi các thành viên chung của các kiểu, chẳng hạn `ex.getMessage()`.
- Nếu các lỗi cần chiến lược xử lý khác nhau, viết các `catch` riêng biệt sẽ dễ hiểu hơn.
- Nếu có `catch` khác phía sau `multi-catch`, quy tắc **nhánh đầu tiên tương thích** vẫn áp dụng.

## 11. `catch` và checked exception được trình biên dịch kiểm tra riêng

Trình biên dịch Java kiểm tra các loại checked exception mà một `try` có thể ném. Nó cũng kiểm tra những trường hợp nhánh `catch` bị che hoàn toàn bởi nhánh trước.

So sánh ba trường hợp:

| Mã | Trạng thái | Giải thích |
| --- | --- | --- |
| `catch (IOException)` rồi `catch (FileNotFoundException)` | Không hợp lệ | Nhánh đầu đã bao phủ mọi `FileNotFoundException`. |
| `catch (FileNotFoundException)` rồi `catch (IOException)` | Hợp lệ khi `try` có khả năng ném các checked exception liên quan | Nhánh riêng xử lý lỗi không có tệp; nhánh chung xử lý phần còn lại. |
| `catch (IOException)` rồi `catch (SQLException)` | Có thể hợp lệ | Hai kiểu không thuộc quan hệ cha/con; `try` phải có khả năng ném những checked exception đó tương ứng. |

Một `try` chỉ có lệnh `System.out.println("ok")` không cho phép tự do thêm `catch (java.io.IOException)` khi không có thao tác nào có thể ném `IOException` checked. Ngược lại, nhánh bắt `RuntimeException` hay `Exception` có những quy tắc cho phép rộng hơn vì chúng bao gồm unchecked exceptions; không suy ra rằng mọi kiểu `catch` đều yêu cầu một câu lệnh `throw` tường minh.

**Thứ tự và tính hợp lệ của `catch` được kiểm tra lúc biên dịch; chọn nhánh cho đối tượng cụ thể xảy ra khi chạy.** Đây là hai bước khác nhau. Kết quả compile-time còn phụ thuộc chữ ký `throws` của phương thức được gọi, không chỉ vào những gì bạn đã quan sát trong một bộ dữ liệu thử.

## 12. `finally` chạy sau khi chọn `catch`, không phải nhánh `catch` kế tiếp

Trong `try-catch-finally`, nếu có một ngoại lệ trong `try`, Java chọn nhánh `catch` phù hợp như trên, thực hiện khối `catch`, sau đó thực thi `finally` theo quy tắc của ngôn ngữ trước khi hoàn tất việc ra khỏi cấu trúc.

```java
try {
    throw new IllegalArgumentException("invalid");
} catch (IllegalArgumentException ex) {
    System.out.println("caught");
} catch (RuntimeException ex) {
    System.out.println("not used");
} finally {
    System.out.println("cleanup");
}
System.out.println("after");
```

Đoạn minh họa trong một phương thức in:

```text
caught
cleanup
after
```

`finally` không phải một `catch` bổ sung và không phụ thuộc nhánh `catch` nào đã chọn; nó có nhiệm vụ thực thi mã dọn dẹp theo quy tắc thông thường. Nếu khối `catch` kết thúc đột ngột do `throw` hoặc `return`, `finally` vẫn được chạy trước khi lý do đó tiếp tục lan đi, miễn chương trình thực sự đi qua cơ chế xử lý Java bình thường.

**Cẩn thận:** nếu `finally` tự ném ngoại lệ hoặc `return`, lý do mới có thể thay thế lỗi/giá trị đang chờ. Trong mã ứng dụng, nên tránh `return` trong `finally` và tránh làm mất ngoại lệ chính trong thao tác dọn dẹp.

## 13. `try-with-resources`: lỗi đóng tài nguyên được xử lý trước khi chọn `catch`

Với `try-with-resources`, tài nguyên sẽ được tự động đóng. Nếu phần thân `try` ném lỗi và `close()` cũng ném lỗi, ngoại lệ từ `close()` thường được đưa vào `getSuppressed()` của ngoại lệ chính. Khi đi tới các nhánh `catch`, **kiểu ngoại lệ chính** quyết định nhánh được chọn.

Ví dụ Java 17+, lưu thành `ResourceCatchDemo.java`:

```java
import java.io.IOException;

public class ResourceCatchDemo {
    static class Resource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("close");
        }
    }

    public static void main(String[] args) {
        try (Resource resource = new Resource()) {
            throw new IllegalStateException("work");
        } catch (IOException ex) {
            System.out.println("io=" + ex.getMessage());
        } catch (IllegalStateException ex) {
            System.out.println("selected=" + ex.getMessage());
            System.out.println("suppressed="
                    + ex.getSuppressed()[0].getMessage());
        }
    }
}
```

```bash
javac -encoding UTF-8 ResourceCatchDemo.java
java ResourceCatchDemo
```

Kết quả:

```text
selected=work
suppressed=close
```

Dù `close()` ném `IOException`, lỗi chính của lần thực thi là `IllegalStateException("work")` phát sinh trong phần thân `try`. `IOException` bị đính kèm làm suppressed, không trở thành một ngoại lệ chính riêng để kích hoạt nhánh `catch (IOException)`. Vì vậy nhánh `catch (IllegalStateException)` được chọn.

Nếu phần thân `try` hoàn tất bình thường nhưng `close()` ném `IOException`, lỗi khi đóng có thể trở thành ngoại lệ chính và nhánh `catch (IOException)` khi đó được chọn. Đây là lý do cần hiểu đúng mối quan hệ giữa lỗi chính và lỗi suppressed thay vì chỉ đếm số ngoại lệ xảy ra.

## 14. Vì sao thường nên bắt các kiểu cụ thể?

Mã sau thường khó bảo trì:

```java
try {
    performBusinessOperation();
} catch (Exception ex) {
    System.out.println("Something went wrong");
}
```

Đây là đoạn minh họa với `performBusinessOperation()` được định nghĩa ở nơi khác. `catch (Exception)` có thể nhận nhiều loại checked exception và `RuntimeException`, khiến chương trình dùng chung một cách xử lý cho những tình huống rất khác nhau. Nó cũng **không bắt `Error`** vì `Error` là nhánh khác của `Throwable`.

Cách thiết kế phù hợp hơn thường là:

1. Xác định những lỗi có thể xử lý tại tầng này (ví dụ `FileNotFoundException` để báo thiếu cấu hình).
2. Đặt `catch` của tình huống cụ thể trước, rồi đến kiểu cha phù hợp nếu cần xử lý rộng hơn.
3. Chỉ dùng `catch (Exception)` tại ranh giới có yêu cầu xử lý lỗi tập trung rõ ràng, và không giả định đã phục hồi được trạng thái ứng dụng.
4. Giữ nguyên đối tượng lỗi hoặc `cause` khi dịch ngoại lệ; không chỉ ghi `ex.getMessage()` rồi bỏ mất stack trace nếu cần điều tra.
5. Không dùng `catch (Throwable)` như một cách “chặn mọi sự cố” trong nghiệp vụ thông thường vì nó còn nhận cả những `Error` nghiêm trọng.

Cần nhớ rằng **không có quy tắc “cứ kiểu càng cụ thể thì JVM sẽ tự ưu tiên”**. JVM xét các nhánh theo thứ tự mã nguồn. Chính lập trình viên phải sắp xếp để các nhánh chuyên biệt được sử dụng đúng.

## 15. Những nhầm lẫn và lỗi thường gặp

| Nhầm lẫn | Thực tế |
| --- | --- |
| “Có ba `catch` tương thích thì cả ba đều chạy.” | Chỉ nhánh đầu tiên phù hợp của cùng `try` được chạy. |
| “JVM tự chọn `catch` cụ thể nhất bất kể thứ tự.” | Java xét thứ tự khai báo; nhánh cha trước nhánh con dẫn tới lỗi biên dịch do che khuất. |
| “Biến được khai báo `IOException` thì ngoại lệ thực tế không thể vào `catch (FileNotFoundException)`.” | Nếu đối tượng runtime là `FileNotFoundException`, nó có thể được nhánh chuyên biệt bắt. |
| “`catch (Exception)` bắt được mọi `Error`.” | Không; `Exception` và `Error` là hai nhánh riêng dưới `Throwable`. |
| “Lỗi ném ra từ `catch` đầu sẽ thử `catch` thứ hai.” | Không; lỗi mới lan ra cấu trúc bên ngoài. |
| “`finally` chạy thay cho một `catch`.” | Không; `finally` có quy tắc thực thi riêng, sau khi khối `try`/`catch` đã được xác định. |
| “Hai kiểu trong `multi-catch` nghĩa là hai `catch` liên tiếp.” | Không; đó là một nhánh có thể nhận một trong các kiểu được khai báo. |
| “Nếu không có ngoại lệ, `catch` cuối là đường mặc định.” | Không có `catch` nào chạy nếu `try` hoàn tất bình thường. |
| “Chỉ xét nhánh `catch` trong cùng phương thức đang lỗi.” | Ngoại lệ có thể lan lên các phương thức gọi cho đến khi gặp bộ xử lý phù hợp. |
| “Lỗi bị suppress khi đóng tài nguyên vẫn tự kích hoạt `catch` theo kiểu riêng.” | Nhánh được chọn theo ngoại lệ chính được ném ra từ cấu trúc `try-with-resources`. |

## 16. Cách tự xác định nhánh nào chạy

Khi đọc một đoạn mã bất kỳ, làm theo bốn bước:

**Bước 1.** Xác định chính xác **đối tượng ngoại lệ đã được ném** và kiểu runtime của nó; xem `cause` và `suppressed` chỉ khi cần truy nguyên, vì chúng không tự trở thành đối tượng đang được `catch` phân loại.

**Bước 2.** Xác định **cấu trúc `try` gần nhất đang bao quanh điểm ném** mà quá trình truyền ngoại lệ đang đi qua. Nếu lỗi xuất phát từ một `catch`, tìm bộ xử lý ở cấu trúc bao ngoài thay vì nhánh `catch` kế bên.

**Bước 3.** Trong cấu trúc `try` đó, đọc từng nhánh `catch` **từ trên xuống**, kiểm tra quan hệ kiểu: kiểu thực tế của lỗi có gán được cho kiểu `catch` này không? Nếu có, dừng tìm tại nhánh đầu tiên.

**Bước 4.** Theo dõi xem nhánh được chọn kết thúc bình thường, `return`, hay `throw` tiếp; sau đó xét `finally` và cấu trúc bao ngoài để xác định lời gọi kết thúc như thế nào.

Ví dụ tư duy:

```java
RuntimeException ex = new NumberFormatException("bad");
try {
    throw ex;
} catch (NumberFormatException caught) {
    System.out.println("A");
} catch (IllegalArgumentException caught) {
    System.out.println("B");
} catch (RuntimeException caught) {
    System.out.println("C");
}
System.out.println("D");
```

Kết quả:

```text
A
D
```

Biến `ex` khai báo kiểu `RuntimeException` nhưng đối tượng thực tế là `NumberFormatException`. Nhánh `A` đầu tiên phù hợp, nhánh `B` và `C` không chạy; cấu trúc `try-catch` hoàn tất bình thường nên in `D`.

## 17. Tự kiểm tra

**Câu 1.** `catch (IOException)` và `catch (Exception)` đều có thể nhận `FileNotFoundException`. Nhánh nào chạy?  
Nhánh `catch` **đầu tiên tương thích theo thứ tự khai báo**. Trong thứ tự vừa nêu, đó là `IOException`.

**Câu 2.** Có thể đặt `catch (Exception)` trước `catch (IOException)` để “ưu tiên ngoại lệ tổng quát” không?  
**Không.** Nhánh `IOException` phía sau bị che hoàn toàn và bị compiler từ chối.

**Câu 3.** Nếu `catch (IOException)` đã bắt lỗi rồi `throw new IllegalStateException(...)`, nhánh `catch (RuntimeException)` kế bên có nhận lỗi mới không?  
**Không.** Ngoại lệ mới cần `catch` của cấu trúc bao ngoài hoặc sẽ tiếp tục truyền lên.

**Câu 4.** `catch (IOException | SQLException ex)` có thực thi hai lần với một lỗi `IOException` không?  
**Không.** Đó là **một** nhánh `catch` duy nhất.

**Câu 5.** `try` hoàn tất bình thường thì nhánh `catch` nào chạy?  
**Không nhánh nào.** Luồng tiếp tục sau cấu trúc `try-catch`, với `finally` được thực thi nếu có.

**Câu 6.** `catch (Exception)` có bắt được `OutOfMemoryError` không?  
**Không.** `OutOfMemoryError` thuộc `Error`. Có thể bắt nó bằng kiểu tương thích như `Error` hoặc `Throwable`, nhưng bắt được không đồng nghĩa có thể phục hồi an toàn.

**Câu 7.** `FileNotFoundException` được lưu trong biến `IOException` rồi ném, `catch (FileNotFoundException)` có thể nhận không?  
**Có.** Chọn bộ xử lý dựa trên kiểu runtime của đối tượng.

**Câu 8.** Phần thân `try-with-resources` ném `IllegalStateException`, còn `close()` ném `IOException` bị suppress. Nhánh `catch (IOException)` có được chọn vì lỗi phụ đó không?  
**Không.** Nhánh xử lý được chọn theo ngoại lệ chính `IllegalStateException` đang được ném.

## Tài liệu tham khảo

- [Java Language Specification, Java SE 21 — §14.20.1: Execution of `try-catch`](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.20.1).
- [JLS 21 — §14.20.2: Execution of `try-finally` and `try-catch-finally`](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.20.2).
- [JLS 21 — §14.20.3: `try-with-resources`](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.20.3).
- [JLS 21 — §11.2.3: Exception Checking](https://docs.oracle.com/javase/specs/jls/se21/html/jls-11.html#jls-11.2.3).

**Ghi nhớ:** Đối với một `try` đang xử lý ngoại lệ, Java dùng **kiểu thực tế của đối tượng ném ra** và **thứ tự `catch` trong mã nguồn**, chọn đúng **nhánh đầu tiên tương thích**. Đặt kiểu con trước kiểu cha, không trông chờ `catch` sau chạy tiếp, và theo dõi `finally` cũng như `try-catch` bên ngoài khi có ngoại lệ mới.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="try-catch-005">Phạm vi của biến trong try và tham số catch</a>

<details>
<summary>Xem chi tiết</summary>

# Phạm vi của biến trong `try` và tham số `catch` trong Java

Một đoạn mã xử lý ngoại lệ trong Java có thể **biên dịch sai dù cách bắt exception hoàn toàn đúng**, chỉ vì biến được khai báo ở một phạm vi không còn hiệu lực, hoặc vì trình biên dịch không thể chứng minh biến đã được gán giá trị trước khi sử dụng. Để viết `try/catch` rõ ràng và an toàn, cần phân biệt ba vấn đề:

1. **Scope (phạm vi nhìn thấy tên)**: từ vị trí nào đến đâu một biến có thể được gọi tên?
2. **Lifetime (thời gian tồn tại của dữ liệu/đối tượng)**: khi thoát khối, đối tượng được tham chiếu có nhất thiết bị hủy hay không?
3. **Definite assignment (chắc chắn đã được gán)**: ở một điểm đọc biến cục bộ, compiler có chứng minh biến đã nhận giá trị trên mọi đường thực thi có thể tới điểm đó không?

`try`, `catch`, `finally` là những khối có dấu ngoặc nhọn riêng. **Biến cục bộ khai báo bên trong thân `try` không nhìn thấy trong thân `catch` hoặc `finally`**. Ngược lại, biến cục bộ được khai báo trước `try` trong cùng phạm vi bao ngoài có thể được dùng bên trong các khối con, miễn là đã được gán hợp lệ. **Tham số `catch (SomeException e)` chỉ thuộc phạm vi của chính thân `catch` tương ứng**, không thuộc `try`, handler khác hoặc các lệnh sau cấu trúc.

Bài này tập trung vào phạm vi biến quanh `try/catch` trong Java 17+, giải thích cả lỗi biên dịch lẫn cách thiết kế mã sao cho xử lý ngoại lệ không làm mất dữ liệu hoặc che mất tín hiệu thất bại.

## 1. Bản đồ phạm vi của `try`, `catch`, `finally`

Xem đoạn mã khung sau. Đây là minh họa quy tắc scope; các lệnh có tên `work`, `recover` và `cleanup` chưa được định nghĩa nên đoạn này không phải chương trình độc lập:

```java
String shared = "trước try";       // Phạm vi bao ngoài

try {
    int onlyInTry = 10;          // Chỉ thuộc khối try
    work(shared, onlyInTry);
} catch (IllegalStateException problem) {
    recover(shared, problem);    // shared và problem nhìn thấy ở đây
    // onlyInTry: KHÔNG nhìn thấy
} finally {
    cleanup(shared);             // shared nhìn thấy ở đây
    // onlyInTry và problem: KHÔNG nhìn thấy
}

System.out.println(shared);      // shared vẫn nhìn thấy
// onlyInTry và problem: KHÔNG nhìn thấy
```

| Biến | Khai báo ở đâu? | Trong `try` | Trong `catch` | Trong `finally` | Sau cấu trúc |
| --- | --- | --- | --- | --- | --- |
| `shared` | Trước `try`, ở khối bao ngoài | Có | Có | Có | Có, đến hết scope bao ngoài |
| `onlyInTry` | Trong thân `try` | Có, từ khai báo tới hết scope | Không | Không | Không |
| `problem` | Tham số `catch` | Không | Có, trong chính thân handler | Không | Không |
| Biến khai báo trong `catch` | Trong thân `catch` | Không | Chỉ chính handler đó | Không | Không |
| Biến khai báo trong `finally` | Trong thân `finally` | Không | Không | Có, trong `finally` | Không |

**Cẩn thận với cách đọc bảng:** “Có” nói về *phạm vi tên biến*, chưa khẳng định biến đã được khởi tạo/gán hợp lệ. Một local variable có thể nhìn thấy trong một khối nhưng vẫn bị trình biên dịch từ chối sử dụng vì chưa **definitely assigned**.

Các nhánh `catch` của cùng một `try` có scope riêng, nên có thể đặt tên tham số giống nhau ở nhiều handler:

```java
try {
    operation();
} catch (java.io.IOException e) {
    System.out.println("I/O: " + e.getMessage());
} catch (IllegalArgumentException e) {
    System.out.println("Input: " + e.getMessage());
}
```

Đây là **khung minh họa**, giả sử `operation()` là phương thức có thể ném `IOException` và `IllegalArgumentException`. Hai tham số cùng tên `e` là hợp lệ vì thuộc **hai khối `catch` tách biệt**, không hoạt động đồng thời.

## 2. Một biến khai báo trong `try` không xuất hiện trong `catch`

Lỗi rất thường gặp là khai báo giá trị trả về bên trong `try` rồi muốn in giá trị đó tại `catch` hoặc ngay sau `try/catch`.

Ví dụ **cố ý không biên dịch**:

```java
try {
    int quantity = Integer.parseInt("12");
    System.out.println(quantity);
} catch (NumberFormatException e) {
    System.out.println(quantity); // Lỗi: không tìm thấy quantity
}
System.out.println(quantity);     // Lỗi: không tìm thấy quantity
```

Tên `quantity` thuộc phạm vi khối `try`. Khi compiler phân tích handler `catch` và câu lệnh bên ngoài, **không có biến cục bộ tên `quantity` nhìn thấy ở hai nơi đó**. Lỗi scope được kiểm tra lúc biên dịch, cho dù bạn tin rằng khi `parseInt` thành công thì biến chắc chắn đã được tạo.

Cách sửa phụ thuộc nhu cầu thực tế:

- Nếu chỉ cần `quantity` để xử lý bên trong đường thành công, hãy **giữ cả phần sử dụng bên trong `try`**; không kéo biến ra ngoài không cần thiết.
- Nếu cần kết quả cho cả đường thành công và đường xử lý ngoại lệ, hãy **khai báo biến ở khối bao ngoài** và bảo đảm mọi đường đi hợp lệ đều gán giá trị trước khi đọc.
- Nếu một thao tác có thể thất bại và không tồn tại giá trị số hợp lệ, có thể dùng **`OptionalInt`, kiểu kết quả có trạng thái**, hoặc trả về sớm từ phương thức; không buộc một số dự phòng trở thành dữ liệu hợp lệ.

**Đừng cố sửa bằng cách tạo lại một biến cùng tên trong `catch`** rồi kỳ vọng đó là biến bên `try`. Hai khai báo ở hai scope khác nhau là **hai biến khác nhau**, không truyền dữ liệu qua lại.

## 3. Ví dụ Java 17 đầy đủ: đặt biến ở đúng phạm vi

Tệp `TryVariableScopeDemo.java`:

```java
public class TryVariableScopeDemo {
    static void process(String input) {
        System.out.println("Input: " + input);
        try {
            int number = Integer.parseInt(input);
            System.out.println("Trong try: " + (number * 2));
        } catch (NumberFormatException e) {
            System.out.println("Trong catch: không phải số nguyên");
        }
        System.out.println("Sau try/catch");
    }

    public static void main(String[] args) {
        process("7");
        process("abc");
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 --release 17 TryVariableScopeDemo.java
java TryVariableScopeDemo
```

**Stdout chính xác** trên terminal hiển thị UTF-8:

```text
Input: 7
Trong try: 14
Sau try/catch
Input: abc
Trong catch: không phải số nguyên
Sau try/catch
```

Trong ví dụ này, `number` chỉ cần phục vụ đường parse thành công, nên phạm vi trong `try` là hợp lý. Khi `"abc"` không chuyển đổi được, exception được bắt; không có giá trị `number` nào cần được dùng trong handler. Lệnh nằm sau cấu trúc chạy trên cả hai đường đi nếu handler kết thúc bình thường.

Điểm đáng chú ý: **scope của `number` không mở rộng sau khi exception được bắt**. Kết thúc bất thường hay thành công của thao tác không làm thay đổi luật scope do compiler xác định từ cấu trúc mã nguồn.

## 4. Biến ở khối ngoài dùng được trong cả `try` và `catch`

Khi cả hai nhánh cần cập nhật một kết quả chung, khai báo biến trước `try`:

```java
String label;

try {
    int quantity = Integer.parseInt(input);
    label = "Số lượng: " + quantity;
} catch (NumberFormatException e) {
    label = "Số lượng không hợp lệ";
}

System.out.println(label);
```

Đây là **khung phương thức minh họa**, giả sử đã có tham số `input` kiểu `String`. `label` nhìn thấy trong cả `try` lẫn `catch` vì khai báo ở khối bao ngoài. Việc đọc `label` sau cấu trúc có thể hợp lệ vì:

- Nếu lời gọi parse và phần gán thành công, `label` được gán ở nhánh `try`.
- Nếu parse ném `NumberFormatException` và handler kết thúc bình thường, `label` được gán ở `catch`.
- Nếu có một exception khác không được bắt, phương thức không tiếp tục bình thường tới `println`, nên đường đó không làm cho biến bị đọc chưa gán.

Java kiểm tra những điều này bằng quy tắc **definite assignment**, không đơn thuần chỉ nhìn thấy có một phép gán ở đâu đó trong mã.

Để dễ kiểm chứng, chương trình độc lập sau gán kết quả ở cả hai nhánh. Lưu thành `SharedResultDemo.java`:

```java
public class SharedResultDemo {
    static String describe(String text) {
        String result;
        try {
            int value = Integer.parseInt(text);
            result = "Hợp lệ: " + value;
        } catch (NumberFormatException e) {
            result = "Không hợp lệ";
        }
        return result;
    }

    public static void main(String[] args) {
        System.out.println(describe("42"));
        System.out.println(describe("x"));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 SharedResultDemo.java
java SharedResultDemo
```

Stdout:

```text
Hợp lệ: 42
Không hợp lệ
```

Biến `result` **không cần gán một giá trị giả ban đầu** vì mọi đường đi tới `return result` đã gán cho nó. Nếu mục tiêu là ngăn caller hiểu nhầm thất bại thành thành công, kiểu dữ liệu kết quả chứa trạng thái hoặc trả về từ từng nhánh cũng có thể rõ ràng hơn.

## 5. Scope khác với definite assignment

Hai lỗi compiler nhìn giống nhau trong mắt người mới học nhưng có nguyên nhân khác:

- **Tên biến ngoài phạm vi**: biến được khai báo trong `try` rồi dùng ở `catch`. Compiler không tìm thấy tên ở nơi đang dùng.
- **Biến chưa chắc đã được gán**: biến được khai báo ngoài `try` và nhìn thấy ở mọi nhánh, nhưng compiler không chứng minh nó đã nhận giá trị trước khi đọc.

Ví dụ thứ hai, **cố ý không biên dịch**:

```java
int quantity;
try {
    quantity = Integer.parseInt(input);
} catch (NumberFormatException e) {
    System.out.println("Đầu vào sai");
}
System.out.println(quantity); // Variable quantity might not have been initialized
```

Đây là khung minh họa có biến `input` từ phạm vi bao ngoài. Vì handler có thể hoàn tất mà không gán `quantity`, câu lệnh cuối có thể đọc biến chưa khởi tạo. Việc `quantity` nằm đúng phạm vi **không làm cho nó được definite assigned**.

Một bản sửa tùy nhu cầu:

```java
int quantity;
try {
    quantity = Integer.parseInt(input);
} catch (NumberFormatException e) {
    throw new IllegalArgumentException("Số lượng phải là số nguyên", e);
}
System.out.println(quantity);
```

Đây vẫn là khung minh họa; đoạn sau `catch` chỉ được thực thi khi phép chuyển đổi thành công, vì handler ném exception mới thay vì hoàn tất bình thường. Trong cách viết này, `quantity` đã được definite assigned trên mọi đường có thể tới lệnh in. Nếu tầng giao tiếp cần xử lý lỗi nhập liệu như kết quả dự kiến, một cách khác là trả trạng thái xác thực thay vì chuyển nó thành exception mới.

Hoặc gán ở cả hai nhánh với ý nghĩa cụ thể:

```java
int quantity;
try {
    quantity = Integer.parseInt(input);
} catch (NumberFormatException e) {
    quantity = 0; // Chỉ đúng nếu 0 là fallback được hợp đồng cho phép.
}
System.out.println(quantity);
```

Không dùng `0` hoặc `-1` như mã lỗi tùy tiện nếu chúng có thể là giá trị hợp lệ; trường hợp này nên biểu diễn rõ thành công/thất bại.

### Vì sao Java nghiêm ngặt?

Biến cục bộ trong Java **không có giá trị mặc định** chỉ vì được khai báo. Trường (field) của một đối tượng có giá trị mặc định theo quy tắc khởi tạo của Java, nhưng **biến cục bộ trong phương thức thì phải được gán trước khi đọc**. Sự khác biệt này giúp compiler ngăn nhiều lỗi dùng dữ liệu chưa khởi tạo.

Nếu bạn biết chắc dữ liệu luôn parse thành công trong một thử nghiệm, compiler vẫn không suy luận rằng nhánh exception là bất khả thi cho mọi đầu vào runtime. Cách viết phải đúng theo những đường thực thi mà ngôn ngữ cho phép.

## 6. Scope của tham số `catch`

Trong:

```java
catch (NumberFormatException e) {
    System.out.println(e.getMessage());
}
```

`e` là **tham số của mệnh đề `catch`**, có kiểu khai báo `NumberFormatException`. Nó được khai báo bởi phần tham số của handler và **nằm trong scope của block handler ấy**. Nó không tồn tại như một biến có thể tham chiếu bằng tên từ thân `try`, từ `finally`, từ handler khác hoặc ở câu lệnh sau toàn bộ cấu trúc.

Ví dụ **cố ý không biên dịch**:

```java
try {
    Integer.parseInt(input);
} catch (NumberFormatException e) {
    System.out.println(e.getClass().getSimpleName());
} finally {
    System.out.println(e.getMessage()); // Không có e trong scope finally
}
System.out.println(e);                 // Không có e ở scope ngoài
```

Nếu cần đưa thông tin về lỗi cho code ở scope ngoài, có thể khai báo một biến ở scope ngoài và gán thông tin thích hợp, nhưng phải cân nhắc xem việc lưu error có làm rối hợp đồng không. Trong đa số trường hợp, hãy xử lý lỗi ngay trong handler hoặc để exception lan truyền.

**Tên `e` không bắt buộc**; có thể dùng `ex`, `cause`, `error`... miễn tuân thủ luật định danh Java và tên biến không xung đột trong các scope chồng lên nhau. Đặt tên giúp diễn đạt trách nhiệm sẽ hữu ích hơn việc cố chấp dùng một ký tự.

## 7. Tham số `catch` là đối tượng lỗi, không phải bản sao của đối tượng

Khi handler nhận `catch (SomeException e)`, biến `e` chứa **tham chiếu** tới đối tượng exception đã được ném. Java truyền tham chiếu theo giá trị khi truyền tham số; bản thân tham số handler cũng chỉ là tên cục bộ. Điều này quan trọng cho scope và việc ném lại:

- `throw e;` ném **cùng đối tượng exception** đang được `e` tham chiếu.
- Đổi tên `e` không đổi loại runtime của đối tượng.
- Nếu dùng một `catch` đơn loại và gán `e` sang đối tượng khác (việc này có thể hợp lệ ở một số ngữ cảnh), `throw e` sau đó sẽ ném đối tượng mới mà biến đang trỏ tới. Như vậy không còn là ném lại cùng lỗi gốc.
- Tham số `catch` hết scope khi kết thúc handler, nhưng đối tượng exception **không bắt buộc bị thu hồi ngay**. Nó có thể vẫn được tham chiếu bởi nơi khác, ví dụ một wrapper exception, cơ chế log, hoặc caller bắt lại.

Lưu `CatchReferenceDemo.java`:

```java
public class CatchReferenceDemo {
    static IllegalArgumentException original;

    static void run() {
        original = new IllegalArgumentException("Lỗi dữ liệu");
        try {
            throw original;
        } catch (IllegalArgumentException e) {
            System.out.println("Trong catch: " + (e == original));
            throw e;
        }
    }

    public static void main(String[] args) {
        try {
            run();
        } catch (IllegalArgumentException outer) {
            System.out.println("Trong main: " + (outer == original));
            System.out.println("Message: " + outer.getMessage());
        }
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 --release 17 CatchReferenceDemo.java
java CatchReferenceDemo
```

Stdout:

```text
Trong catch: true
Trong main: true
Message: Lỗi dữ liệu
```

Tên `e` trong handler trong và `outer` trong handler ngoài khác nhau, nhưng chúng có thể tham chiếu **cùng một instance**. Ví dụ dùng field `original` chỉ để kiểm tra identity; không nên tạo state toàn cục chứa “exception mới nhất” để giao tiếp giữa các thread trong chương trình thực.

## 8. Các handler `catch` cùng cấp có phạm vi riêng

Hai handler của cùng một câu lệnh `try` có thể dùng cùng tên tham số:

```java
try {
    potentiallyFails();
} catch (java.io.IOException e) {
    System.out.println("I/O: " + e.getMessage());
} catch (IllegalArgumentException e) {
    System.out.println("Input: " + e.getMessage());
}
```

Đây là khung minh họa, giả định `potentiallyFails()` khai báo/cho phép những loại lỗi tương ứng. Java cho phép hai tên `e` vì mỗi tên chỉ tồn tại trong scope của handler riêng.

Tuy nhiên, **không được tạo thêm một biến cùng tên `e` ngay trong thân handler** khi nó trùng với tham số đang có:

```java
try {
    Integer.parseInt("x");
} catch (NumberFormatException e) {
    String e = "bad"; // Lỗi biên dịch: e đã được định nghĩa tại scope này
}
```

Thay `String e` bằng tên `message` sẽ hợp lệ.

Một nhầm lẫn tương tự là cho rằng `catch` thứ hai có thể đọc biến cục bộ được khai báo trong `catch` thứ nhất. Không được, vì hai handler là hai scope độc lập.

## 9. Shadowing và xung đột tên với biến bao ngoài

Java cho phép một biến cục bộ hoặc tham số handler che khuất (**shadow**) một field có cùng tên trong một phạm vi gần hơn; nhưng không cho phép tùy tiện khai báo biến cục bộ mới trùng tên với một biến cục bộ đã còn trong phạm vi bao quanh.

### Trùng với biến cục bộ bao ngoài: lỗi biên dịch

```java
void demonstrate() {
    String e = "đã có";
    try {
        Integer.parseInt("x");
    } catch (NumberFormatException e) { // Không hợp lệ: e đã có ở outer local scope
        System.out.println(e);
    }
}
```

Đây là phương thức minh họa **cố ý không biên dịch**. Tham số `catch` gây xung đột với biến cục bộ `e` ở phạm vi bao ngoài vẫn đang hoạt động. Sửa bằng tên khác, chẳng hạn `catch (NumberFormatException cause)`.

### Trùng với field: có thể hợp lệ

Ví dụ hoàn chỉnh trong `FieldShadowDemo.java`:

```java
public class FieldShadowDemo {
    private String error = "field";

    void demonstrate() {
        try {
            Integer.parseInt("invalid");
        } catch (NumberFormatException error) {
            System.out.println("Catch: " + error.getClass().getSimpleName());
            System.out.println("Field: " + this.error);
        }
    }

    public static void main(String[] args) {
        new FieldShadowDemo().demonstrate();
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 FieldShadowDemo.java
java FieldShadowDemo
```

Stdout:

```text
Catch: NumberFormatException
Field: field
```

Trong handler, tên `error` trỏ tới tham số `catch`. Trường `error` của đối tượng vẫn tồn tại và có thể truy cập bằng `this.error`. Đây là **shadowing field**, khác với khai báo trùng một biến local trong scope lồng nhau đang hoạt động.

Nguyên tắc thiết kế: đặt tên tham số handler rõ ràng để tránh nhầm với state của đối tượng; đừng dựa vào shadowing khi không cần thiết.

## 10. `catch` một loại và tính `final` / `effectively final`

Một tham số `catch` đơn loại như `catch (IOException e)` thường có thể được gán lại nếu không khai báo `final`. Tuy vậy, **gán lại tham số handler thường là cách viết khó hiểu**: nó đánh mất tên tham chiếu ban đầu, có thể phá tác dụng của phân tích *precise rethrow*, và làm khó chẩn đoán lỗi.

So sánh:

```java
catch (IllegalArgumentException e) {
    // e không bị gán lại: effectively final.
    throw e;
}
```

với:

```java
catch (IllegalArgumentException e) {
    e = new IllegalArgumentException("Lỗi đã bị thay");
    throw e; // Ném đối tượng mới, không còn giữ identity của lỗi ban đầu.
}
```

Đây là **hai đoạn handler minh họa riêng biệt**, không đặt liên tiếp như một chương trình độc lập. Tham số handler đầu là *effectively final* vì không có phép gán lại; trong handler thứ hai, `e` không còn effectively final.

Có thể yêu cầu bất biến bằng từ khóa `final`:

```java
catch (final IllegalArgumentException e) {
    System.out.println(e.getMessage());
    // e = new IllegalArgumentException(); // Không hợp lệ
}
```

Cần phân biệt **không gán lại biến tham chiếu** với **đối tượng bất biến**. `final` hoặc *effectively final* chỉ hạn chế việc biến `e` trỏ sang instance khác; các phương thức trên đối tượng exception vẫn có thể sửa những thuộc tính cho phép thay đổi, chẳng hạn `addSuppressed` hoặc `setStackTrace`.

## 11. Multi-catch: một tham số có nhiều kiểu và ngầm định `final`

Java hỗ trợ multi-catch:

```java
try {
    readOrParse();
} catch (java.io.IOException | IllegalArgumentException e) {
    System.out.println(e.getClass().getSimpleName());
}
```

Đây là khung minh họa; phương thức `readOrParse()` cần được định nghĩa để chương trình biên dịch. Ở đây có **một tham số `e`** nhưng nó có thể nhận exception thuộc một trong hai nhánh kiểu khai báo. Java áp dụng các quy tắc chung của một loại kiểu tổng hợp/kiểu hợp thích hợp để kiểm tra các lời gọi trên `e`.

Điểm đáng nhớ:

- Tham số trong multi-catch là **implicitly final**: không được gán lại `e`.
- Các loại ở dấu `|` không được có quan hệ cha–con trực tiếp/gián tiếp khiến một loại là subtype của loại còn lại; ví dụ `IOException | Exception` là không hợp lệ.
- Scope của `e` vẫn chỉ ở **thân handler multi-catch**.
- Tham số chỉ lưu một đối tượng exception cụ thể tại mỗi lần handler chạy, không đồng thời lưu hai đối tượng.

Ví dụ đầy đủ trong `MultiCatchScopeDemo.java`:

```java
import java.io.IOException;

public class MultiCatchScopeDemo {
    static void process(String input) throws IOException {
        if (input.equals("io")) {
            throw new IOException("Hỏng nguồn");
        }
        int value = Integer.parseInt(input);
        System.out.println("Số: " + value);
    }

    public static void main(String[] args) {
        for (String input : new String[] {"4", "io", "abc"}) {
            try {
                process(input);
            } catch (IOException | NumberFormatException e) {
                System.out.println("Bắt: " + e.getClass().getSimpleName());
            }
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 MultiCatchScopeDemo.java
java MultiCatchScopeDemo
```

Stdout:

```text
Số: 4
Bắt: IOException
Bắt: NumberFormatException
```

Phạm vi `e` chấm dứt sau handler. Nếu thêm `e = new IOException("Khác")` trong multi-catch, chương trình bị từ chối tại compile time vì tham số này ngầm định là `final`.

## 12. Precise rethrow liên quan thế nào đến phạm vi và việc gán lại tham số `catch`?

Từ Java 7, khi tham số handler là `final` hoặc *effectively final*, trình biên dịch có thể thực hiện phân tích **precise rethrow**: dù tham số khai báo kiểu rộng như `Exception`, tập checked exception thực sự có thể ném lại bằng `throw e` được xác định hẹp hơn từ `try`.

Ví dụ hợp lệ trong `PreciseRethrowScopeDemo.java`:

```java
import java.io.IOException;

public class PreciseRethrowScopeDemo {
    static void read() throws IOException {
        throw new IOException("Không đọc được");
    }

    static void relay() throws IOException {
        try {
            read();
        } catch (Exception e) {
            System.out.println("Trong catch");
            throw e;
        }
    }

    public static void main(String[] args) {
        try {
            relay();
        } catch (IOException e) {
            System.out.println("Ở caller: " + e.getMessage());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 PreciseRethrowScopeDemo.java
java PreciseRethrowScopeDemo
```

Stdout:

```text
Trong catch
Ở caller: Không đọc được
```

Dù bắt `catch (Exception e)`, phương thức `relay` chỉ cần `throws IOException` trong ví dụ này, vì `e` không bị gán lại và Java xác định checked exception có thể phát sinh từ thân `try` là `IOException`. Unchecked exception nếu có không bắt buộc khai báo.

Nếu gán lại `e` trong cùng handler, Java không thể áp dụng cùng quy tắc thu hẹp kiểu như trên. Chẳng hạn đoạn sau **cố ý không biên dịch** với chữ ký chỉ `throws IOException`:

```java
static void relayBad() throws IOException {
    try {
        throw new IOException("Lỗi gốc");
    } catch (Exception e) {
        e = new IOException("Lỗi khác");
        throw e; // Có thể bị kiểm tra theo kiểu rộng Exception.
    }
}
```

Ví dụ nhấn mạnh **scope và trạng thái gán lại của tham số handler ảnh hưởng nghĩa vụ checked exception ở compile time**. Đừng gán lại tham số `catch` khi mục tiêu là lan truyền nguyên bản; đặt một biến khác nếu thực sự cần đại diện cho một lỗi mới, và khai báo hợp đồng phù hợp.

## 13. `try-with-resources` có scope riêng cho resource

Trong `try-with-resources`, tài nguyên khai báo ở phần đầu câu lệnh `try` có phạm vi trong **khối thân `try`**, không thuộc scope của handler `catch`, `finally` gắn liền hoặc các lệnh sau cấu trúc.

Ví dụ **cố ý không biên dịch** nếu thêm các dòng comment thành mã:

```java
try (java.io.StringReader reader = new java.io.StringReader("abc")) {
    System.out.println(reader.read());
} catch (java.io.IOException e) {
    // reader.read(); // Không có reader trong scope catch.
}
// reader.read(); // Không có reader sau try-with-resources.
```

`reader` được khai báo tại resource specification, được khởi tạo trước khi vào thân `try` và được Java tự động đóng khi rời phạm vi tài nguyên. Tham số `e` của handler vẫn chỉ sống trong thân `catch`.

Một chương trình độc lập giúp thấy phạm vi và thứ tự đóng, lưu `ResourceScopeDemo.java`:

```java
public class ResourceScopeDemo {
    static class Resource implements AutoCloseable {
        private final String name;

        Resource(String name) {
            this.name = name;
            System.out.println("Mở: " + name);
        }

        String read() {
            return "Dữ liệu từ " + name;
        }

        @Override
        public void close() {
            System.out.println("Đóng: " + name);
        }
    }

    public static void main(String[] args) {
        String result = "Chưa có dữ liệu";

        try (Resource resource = new Resource("A")) {
            result = resource.read();
            System.out.println("Trong try: " + result);
            throw new IllegalStateException("Lỗi sau khi đọc");
        } catch (IllegalStateException e) {
            System.out.println("Catch: " + e.getMessage());
        } finally {
            System.out.println("Finally");
        }

        System.out.println("Result ngoài: " + result);
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 ResourceScopeDemo.java
java ResourceScopeDemo
```

Stdout:

```text
Mở: A
Trong try: Dữ liệu từ A
Đóng: A
Catch: Lỗi sau khi đọc
Finally
Result ngoài: Dữ liệu từ A
```

Ở nhánh thành công của việc mở và đọc tài nguyên, `result` được gán trước lệnh `throw`. Tuy nhiên constructor hoặc `read()` vẫn có thể thất bại trước phép gán rồi đi vào `catch`; khởi tạo `result` với giá trị dự phòng giúp chương trình đáp ứng quy tắc **definite assignment**. Tài nguyên `resource` bị đóng **trước** khi handler được thực thi. Scope của `resource` không mở rộng ra handler, nhưng biến `result` được khai báo ở khối ngoài nên vẫn thấy ở cuối.

**Scope và cleanup là hai khái niệm độc lập:** một resource hết scope sau `try`, nhưng việc gọi `close()` có thời điểm cụ thể do `try-with-resources` quản lý. Một đối tượng đã đóng vẫn có thể được tham chiếu từ nơi khác nếu tham chiếu được giữ lại; điều đó không có nghĩa nó còn sử dụng tài nguyên được phép.

## 14. `try-with-resources` dùng biến đã khai báo trước (Java 9+)

Từ Java 9, resource specification có thể nhận một biến đã được khai báo ngoài `try`, nếu biến ấy là `final` hoặc *effectively final* và kiểu triển khai `AutoCloseable`. Khi đó, **phạm vi tên biến vẫn là scope nơi nó được khai báo ban đầu**.

Ví dụ Java 17 độc lập, lưu `ExistingResourceDemo.java`:

```java
public class ExistingResourceDemo {
    static class Resource implements AutoCloseable {
        private boolean closed;

        String read() {
            if (closed) {
                throw new IllegalStateException("Đã đóng");
            }
            return "ok";
        }

        boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            closed = true;
            System.out.println("Đang đóng");
        }
    }

    public static void main(String[] args) {
        Resource resource = new Resource();

        try (resource) {
            System.out.println(resource.read());
        }

        System.out.println("Có thể gọi tên resource: " + resource.isClosed());
    }
}
```

Chạy:

```bash
javac --release 17 ExistingResourceDemo.java
java ExistingResourceDemo
```

Stdout:

```text
ok
Đang đóng
Có thể gọi tên resource: true
```

Biến `resource` được khai báo trước `try`, vì vậy tên vẫn trong scope sau cấu trúc, nhưng đối tượng đã được đóng. Điều kiện *effectively final* yêu cầu không gán lại biến `resource` sau khi gán ban đầu; nếu viết `resource = new Resource()` sau đó, compiler sẽ từ chối sử dụng `resource` theo dạng `try (resource)` này.

Từ Java 9, dạng truyền biến tồn tại này là cú pháp hợp lệ. Đối với Java 7/8, cú pháp quen thuộc là khai báo resource ngay trong phần `try (...)`, hoặc dùng một khai báo biến resource mới phù hợp với ngôn ngữ phiên bản đó.

## 15. Scope của `finally` và biến được chia sẻ

`finally` là một khối riêng. Nó không được quyền truy cập biến local khai báo bên trong `try` hoặc tham số `catch`, nhưng có thể truy cập biến ở scope bao ngoài.

```java
int attempts = 0;

try {
    attempts++;
    execute();
} catch (RuntimeException e) {
    System.out.println("Lỗi: " + e.getMessage());
} finally {
    System.out.println("Số lần thử: " + attempts);
}
```

Đây là mã minh họa, `execute()` chưa được định nghĩa. `attempts` nằm ở scope bao ngoài và đã có giá trị ban đầu, nên `finally` có thể đọc. Còn nếu viết `int attempts = 0;` **chỉ bên trong `try`**, `finally` không được quyền gọi tên biến ấy.

Nếu chỉ cần cleanup resource, ưu tiên `try-with-resources` với các đối tượng `AutoCloseable`. Nếu dùng `finally` cho việc khác, hạn chế `return` hoặc ném exception mới trong đó vì có thể ghi đè kết quả hoặc che exception đang lan truyền.

Không thể suy luận rằng có biến nhìn thấy trong `finally` là có giá trị đúng: nếu trạng thái chỉ được cập nhật ở giữa `try` rồi exception xảy ra trước đó, `finally` phải chịu được trạng thái cập nhật một phần.

## 16. Tham số `catch` và lambda: quy tắc biến được capture

Java cho phép lambda hoặc anonymous class sử dụng biến local từ phạm vi bao ngoài khi biến đó là `final` hoặc *effectively final*. Tham số `catch` cũng tuân thủ cơ chế này. Handler có thể tạo lambda sử dụng `e`, miễn là không gán lại `e` khi nó được capture.

Ví dụ hoàn chỉnh, lưu `CatchLambdaDemo.java`:

```java
public class CatchLambdaDemo {
    public static void main(String[] args) {
        Runnable report;

        try {
            Integer.parseInt("wrong");
            report = () -> System.out.println("Không có lỗi");
        } catch (NumberFormatException e) {
            report = () -> System.out.println(
                    "Lambda đọc: " + e.getClass().getSimpleName());
        }

        report.run();
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 CatchLambdaDemo.java
java CatchLambdaDemo
```

Stdout:

```text
Lambda đọc: NumberFormatException
```

`report` được khai báo ở scope ngoài và gán trong các nhánh. Lambda được tạo bên trong `catch` có quyền capture tham số `e` khi `e` là effectively final. **Kết thúc scope `catch` không khiến đối tượng ngoại lệ tự bị hủy**: lambda có thể giữ tham chiếu cần dùng khi chạy sau đó.

Đây là lý do cần cân nhắc khi giữ lambda hoặc callback lâu dài: một exception có stack trace, cause và dữ liệu ngữ cảnh; việc capture có thể giữ nó trong bộ nhớ lâu hơn mong muốn hoặc vô tình làm lộ nội dung nếu chuyển qua các tầng không phù hợp.

Nếu gán lại `e` trong handler mà cũng muốn capture nó, compiler sẽ báo lỗi vì biến được capture không còn effectively final. Cách rõ hơn là giữ `e` nguyên trạng và, nếu cần, capture một chuỗi message hoặc mã lỗi đã được xử lý.

## 17. Scope không phải lúc nào cũng là “vòng đời đối tượng”

Một hiểu nhầm phổ biến: sau khi rời `catch`, vì tên `e` không còn trong scope, đối tượng exception cũng nhất định biến mất. Java không hoạt động như vậy.

- **Lexical scope** của tên `e` kết thúc tại đóng ngoặc của handler.
- **Reference reachability** của đối tượng exception phụ thuộc việc có nơi khác vẫn giữ tham chiếu hay không.
- Bộ thu gom rác (GC) chỉ có thể thu hồi những đối tượng không còn reachable theo các quy tắc của JVM; không có thời điểm thu hồi chính xác bảo đảm ngay khi thoát một khối.
- Nếu ném lại cùng đối tượng bằng `throw e`, caller có thể bắt được chính instance đó.
- Nếu bọc ngoại lệ bằng `new IllegalStateException("...", e)`, wrapper giữ `e` ở `cause`; exception gốc có thể tiếp tục sống theo vòng đời wrapper.
- Nếu capture `e` trong lambda được lưu lại, object có thể còn reachable dù handler đã kết thúc.

Đây là sự khác nhau giữa **tên biến có thể truy cập ở đâu** và **dữ liệu còn được tham chiếu ở đâu**. Tương tự, đóng resource không đồng nghĩa xóa ngay đối tượng Java chứa resource.

## 18. Phạm vi và xử lý ngoại lệ khi có vòng lặp

Biến khai báo trong thân vòng lặp hoặc trong `try` của mỗi lượt lặp có scope theo cấu trúc lặp. Biến ngoài vòng lặp có thể tích lũy kết quả.

Ví dụ `LoopScopeDemo.java`:

```java
public class LoopScopeDemo {
    public static void main(String[] args) {
        int validCount = 0;

        for (String text : new String[] {"2", "bad", "5"}) {
            try {
                int number = Integer.parseInt(text);
                System.out.println("Giá trị: " + number);
                validCount++;
            } catch (NumberFormatException e) {
                System.out.println("Bỏ qua: " + text);
            }
        }

        System.out.println("Hợp lệ: " + validCount);
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 LoopScopeDemo.java
java LoopScopeDemo
```

Stdout:

```text
Giá trị: 2
Bỏ qua: bad
Giá trị: 5
Hợp lệ: 2
```

`number` chỉ ở trong khối `try` của mỗi lượt; `e` chỉ trong handler của lần lỗi tương ứng; `text` được khai báo ở phần enhanced `for` và nhìn thấy trong cả `try` và `catch` bên trong thân vòng lặp; `validCount` nằm ngoài vòng lặp nên dùng được sau đó.

Nếu một exception bị bắt **bên ngoài toàn bộ vòng lặp**, vòng lặp bị thoát khi exception lan tới handler ngoài; Java không tự quay lại phần tử tiếp theo. Đặt `try/catch` ở đúng vị trí giúp biến và phạm vi xử lý phản ánh chủ ý: xử lý lỗi từng phần tử hay hủy cả đợt.

## 19. Mẫu thiết kế rõ ràng: đưa dữ liệu thành kết quả thay vì chia sẻ biến không an toàn

Không phải mọi xử lý lỗi đều nên cố tạo biến chung giữa `try` và `catch`. Nếu dữ liệu thực sự chỉ tồn tại khi thành công, có thể biểu diễn **kết quả có trạng thái**.

Java 17 hỗ trợ `record` và `sealed interface`, hữu ích cho ví dụ này. Lưu `ParseResultDemo.java`:

```java
public class ParseResultDemo {
    sealed interface ParseResult permits Success, Invalid {
    }

    record Success(int value) implements ParseResult {
    }

    record Invalid(String reason) implements ParseResult {
    }

    static ParseResult parse(String input) {
        try {
            int number = Integer.parseInt(input);
            return new Success(number);
        } catch (NumberFormatException e) {
            return new Invalid("Không phải số nguyên");
        }
    }

    static void describe(String input) {
        ParseResult result = parse(input);

        if (result instanceof Success success) {
            System.out.println("OK: " + success.value());
        } else if (result instanceof Invalid invalid) {
            System.out.println("Sai: " + invalid.reason());
        }
    }

    public static void main(String[] args) {
        describe("10");
        describe("x");
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 ParseResultDemo.java
java ParseResultDemo
```

Stdout:

```text
OK: 10
Sai: Không phải số nguyên
```

Dữ liệu `number` nằm ở `try` của phương thức parse, chỉ được chuyển sang `Success` khi parse thành công. Handler không cần gọi tên biến `number` vì chẳng có giá trị thành công hợp lệ nào được tạo trên đường lỗi. Bên ngoài, `result` là một giá trị luôn được trả về theo hợp đồng; kiểm tra kiểu quyết định sử dụng đúng nhánh.

Những biến pattern `success` và `invalid` cũng có phạm vi do **flow scoping** của pattern matching quy định, nhưng đây là cơ chế riêng của Java 17. Nó không mở rộng phạm vi của biến trong `try` hay tham số `catch`. Ví dụ chọn `if/else if` để người đọc thấy rõ mỗi giá trị chỉ được sử dụng sau khi kiểm tra kiểu thích hợp.

## 20. Các lỗi biên dịch thường gặp và cách sửa

| Thông báo hoặc hiện tượng | Nguyên nhân thường gặp | Cách sửa |
| --- | --- | --- |
| `cannot find symbol: variable x` khi đọc từ `catch` | `x` khai báo chỉ trong `try` | Đưa khai báo ra scope ngoài nếu thật sự cần hoặc xử lý `x` trong `try` |
| `variable x might not have been initialized` | `x` có scope đúng nhưng có đường đi không gán | Gán ở mọi đường bình thường, return/throw sớm, hoặc mô hình hóa kết quả rõ ràng |
| `variable e is already defined` | Tạo biến local `e` trùng tham số handler hoặc outer local | Đổi tên để không chồng scope |
| `multi-catch parameter e may not be assigned` | Gán lại tham số `catch (A \| B e)` | Không gán lại; tạo biến khác nếu cần |
| `local variables referenced from a lambda expression must be final or effectively final` | Capture tham số/biến đã bị gán lại | Giữ tham số effectively final hoặc capture giá trị khác |
| `unreported exception ... must be caught or declared to be thrown` | Một checked exception (kể cả khi `throw e`) chưa được xử lý/khai báo | Bắt phù hợp hoặc thêm `throws` theo hợp đồng |
| Handler không nhìn thấy resource đã khai báo trong `try (...)` | Resource specification tạo scope chỉ trong thân `try` | Dùng dữ liệu trước khi ra khỏi khối hoặc khai báo tham chiếu outer nếu thực sự cần |
| Cần dùng exception sau handler nhưng tên `e` mất | `e` chỉ trong scope handler | Xử lý trong handler, ném lại, hoặc chuyển thông tin ra ngoài bằng giá trị có kiểu phù hợp |

**Không nên “sửa” mọi lỗi scope bằng cách khai báo biến thành field hoặc `static`.** Điều đó mở rộng thời gian sống và chia sẻ state, có thể gây tranh chấp khi đa luồng và khó kiểm thử. Giữ phạm vi nhỏ nhất đáp ứng nhu cầu thực tế.

## 21. Lưu ý về ngoại lệ và đường đi thực thi khi phân tích biến

Compiler phân tích phạm vi biến **theo cấu trúc từ vựng của mã**, còn việc exception làm thay đổi điểm tiếp tục thực thi là **control flow**. Hai lớp quy tắc tương tác nhưng không thể thay thế nhau.

Ví dụ, nếu `try` gọi một phương thức có thể ném exception **trước** lệnh gán, nhánh `catch` có thể chạy khi biến outer chưa nhận giá trị. Nếu `catch` gán một fallback hợp lệ, biến mới trở thành definitely assigned sau toàn bộ cấu trúc. Nếu handler kết thúc bằng `return` hoặc `throw`, Java biết không có đường bình thường đi qua handler đó tới lệnh đọc biến.

Một sai lầm khác là đặt code đọc kết quả sau handler chỉ vì “lúc nào cũng có kết quả”. Nếu một lời gọi thất bại giữa chừng, **không thể coi dữ liệu trước lỗi là kết quả hoàn chỉnh**. Cần đảm bảo tính nhất quán của side effect, hợp đồng trả về và trạng thái chương trình, ngoài việc thoả compiler.

Cũng không nhầm checked exception với biến scope: `IOException` được khai báo bằng `throws` là vấn đề về **hợp đồng ném lỗi**, còn `cannot find symbol` với biến trong `try` là vấn đề **phạm vi tên**. Thêm `throws` không mở rộng scope biến.

## 22. Bài tập tự kiểm tra

**Câu 1.** Biến `x` khai báo trong `try` có được gọi tên trong `catch` gắn với `try` đó không?  
**Đáp án:** Không. Hai thân khối khác nhau; `x` chỉ nằm trong scope của thân `try`.

**Câu 2.** Biến `result` khai báo trước `try` nhưng chỉ gán khi parse thành công. Nếu `catch` chỉ in lỗi rồi kết thúc bình thường, có đọc `result` ở sau cấu trúc được không?  
**Đáp án:** Không chắc. Compiler có thể báo chưa được gán vì đường handler vẫn có thể tới chỗ đọc. Cần xử lý tất cả đường đi hoặc return/throw khỏi nhánh lỗi.

**Câu 3.** Hai nhánh `catch` khác nhau có thể đều dùng tham số tên `e` không?  
**Đáp án:** Có. Scope của các tham số thuộc hai handler riêng biệt.

**Câu 4.** Có thể khai báo `String e` bên trong handler đã có tham số `catch (IOException e)` không?  
**Đáp án:** Không. Đó là xung đột khai báo trong phạm vi đã có `e`.

**Câu 5.** Tham số multi-catch có thể gán lại không?  
**Đáp án:** Không. Tham số của multi-catch là implicitly final.

**Câu 6.** `catch (Exception e) { throw e; }` có luôn buộc khai báo `throws Exception` không?  
**Đáp án:** Không. Với tham số final/effectively final, Java có thể dùng precise rethrow để xác định tập checked exception hẹp hơn từ thân `try`.

**Câu 7.** Resource khai báo trong `try (Resource r = ...)` có nhìn thấy trong `catch` trực tiếp gắn với cấu trúc không?  
**Đáp án:** Không. Resource specification giới hạn scope của `r` trong thân `try`.

**Câu 8.** Với `Resource r = new Resource(); try (r) {...}` trên Java 17, có thể gọi tên `r` ở sau `try` không?  
**Đáp án:** Có, vì `r` được khai báo ngoài `try`. Tuy nhiên resource đã được cố đóng khi rời khối.

**Câu 9.** Từ khóa `final` trên tham số `catch` có làm object exception bất biến tuyệt đối không?  
**Đáp án:** Không. Nó cấm gán lại tham số, chứ không ngăn các phương thức hợp lệ sửa thuộc tính của đối tượng.

**Câu 10.** Nếu lambda được tạo trong `catch` giữ tham chiếu `e` và chạy sau handler, liệu có vi phạm scope không?  
**Đáp án:** Không, nếu `e` được capture hợp lệ (final/effectively final). Lambda giữ tham chiếu object; việc tên `e` hết scope ở mã ngoài handler không làm lambda mất tham chiếu đã capture.

## 23. Checklist khi viết `try/catch`

Trước khi hoàn tất một phương thức xử lý exception, hãy rà soát:

1. **Tên biến được khai báo ở scope nào?** Chỉ đưa khai báo ra ngoài `try` khi các nhánh thực sự cần cùng biến.
2. **Có dùng biến trong `catch`/`finally` mà nó chỉ thuộc `try` không?** Nếu có, chỉnh lại cấu trúc dữ liệu.
3. **Biến outer được gán trên mọi đường đi bình thường chưa?** Xét cả exception xảy ra trước phép gán và việc handler hoàn tất.
4. **Tham số `catch` có trùng tên local ngoài hoặc biến trong handler không?** Tránh xung đột scope.
5. **Có gán lại tham số handler không?** Giữ nguyên exception gốc khi ném lại và bảo toàn khả năng precise rethrow.
6. **Multi-catch có đúng luật kiểu và final không?** Không gán lại tham số, không ghép hai loại có quan hệ cha–con.
7. **Resource có được dùng trong phạm vi hợp lệ không?** Nhớ rằng resource khai báo tại `try (...)` không nhìn thấy ở `catch` và được tự đóng.
8. **Lambda có capture biến hợp lệ không?** Kiểm tra final/effectively final và nguy cơ giữ tham chiếu dài hạn.
9. **Có dùng kết quả chưa hoàn chỉnh sau đường thất bại không?** Đảm bảo trạng thái và ý nghĩa nghiệp vụ, không chỉ khả năng biên dịch.
10. **Ví dụ và test có bao phủ cả đường thành công lẫn đường lỗi không?** Đây là cách tốt nhất để xác nhận nhánh scope/assignment được hiểu chính xác.

## 24. Ghi nhớ

**Trong Java, thân `try`, từng thân `catch` và thân `finally` là các phạm vi biến riêng.** Biến khai báo trong `try` không gọi được ở `catch`; tham số `catch` chỉ gọi được trong handler tương ứng; biến khai báo trước cấu trúc có thể xuất hiện ở các khối con và ở phía sau, miễn tuân thủ **definite assignment**.

Với multi-catch, tham số handler ngầm định là `final`. Với catch một loại, giữ tham số *effectively final* là thực hành tốt để bảo toàn identity và hỗ trợ **precise rethrow**. `try-with-resources` có quy tắc scope của resource riêng; trường hợp sử dụng biến đã khai báo trước (`try (resource)`, Java 9+) cần phân biệt scope của tên với việc tài nguyên đã đóng.

Hãy giữ biến ở **phạm vi nhỏ nhất có ý nghĩa**, gán rõ trên những đường đi có thể tiếp tục, và không biến thất bại thành dữ liệu thành công giả chỉ để giải quyết lỗi scope. Cách nghĩ đó giúp mã `try/catch` vừa biên dịch đúng vừa phản ánh trung thực luồng xử lý ngoại lệ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="try-catch-006">Biến cục bộ và yêu cầu được gán giá trị chắc chắn</a>

<details>
<summary>Xem chi tiết</summary>

# Biến cục bộ và yêu cầu được gán giá trị chắc chắn trong try/catch Java

Khi dùng `try/catch`, một biến cục bộ có thể **được khai báo** ở đầu phương thức nhưng chỉ **được gán giá trị** trong một số nhánh. Nếu chương trình đọc biến sau `try/catch`, trình biên dịch Java phải chứng minh biến đó đã được gán trên **mọi đường thực thi có thể đi tới điểm đọc**. Quy tắc này gọi là **definite assignment** (DA, gán giá trị chắc chắn).

Ví dụ phổ biến:

~~~java
int result;
try {
    result = Integer.parseInt(text);
} catch (NumberFormatException e) {
    System.out.println("Không phải số nguyên");
}
System.out.println(result); // Lỗi biên dịch: result có thể chưa được gán.
~~~

Đây là đoạn minh họa nằm trong phương thức có biến `text`. Nếu `Integer.parseInt(text)` ném `NumberFormatException`, điều khiển đi vào `catch` **trước khi phép gán `result = ...` hoàn tất**; khối `catch` chỉ in thông báo, nên tại `System.out.println(result)`, biến có thể chưa có giá trị. Java từ chối ngay khi biên dịch, kể cả bạn chỉ định gọi phương thức với chuỗi số hợp lệ.

Bài học giải thích cách Java phân tích các đường luồng điều khiển trong `try/catch`, cách sửa mã đúng nghiệp vụ, tác động của `return`, `throw`, `finally`, biến `final`, phạm vi biến và các trường hợp dễ nhầm.

## 1. Định nghĩa: definite assignment khác giá trị mặc định

**Biến cục bộ (local variable)** khai báo trong phương thức hoặc khối lệnh không được Java tự gán `0`, `false` hay `null` chỉ vì kiểu của nó là `int`, `boolean` hay kiểu tham chiếu.

~~~java
static void example() {
    int count;
    // System.out.println(count); // Không biên dịch: chưa được gán.
    count = 3;
    System.out.println(count);     // Hợp lệ.
}
~~~

Trong ví dụ, `count` tồn tại trong phạm vi cú pháp từ vị trí khai báo, nhưng chỉ được phép đọc sau khi Java xác định phép gán cần thiết đã xảy ra.

Cần phân biệt ba khái niệm:

| Khái niệm | Ý nghĩa |
| --- | --- |
| **Scope (phạm vi)** | Vị trí trong mã nơi tên biến có thể được tham chiếu |
| **Definite assignment** | Tại điểm đọc, trình biên dịch chứng minh biến đã được gán trên mọi đường tới được |
| **Giá trị runtime** | Giá trị cụ thể khi đường thực thi thật sự chạy tới đó |

Ví dụ: một biến khai báo ngoài `try` có thể còn trong phạm vi sau `catch`, nhưng **vẫn không được đọc** nếu chưa đạt điều kiện DA.

Đừng nhầm với **field** của một đối tượng hoặc `static field`: field được khởi tạo mặc định nếu không được gán tường minh (`0`, `false`, `null` tùy kiểu). Tương tự, phần tử mảng mới tạo cũng có giá trị mặc định. Quy tắc cần chứng minh gán trước khi đọc ở đây đặc biệt quan trọng đối với **biến cục bộ**.

## 2. Vì sao `try/catch` làm xuất hiện đường chưa gán?

Xem đoạn mã:

~~~java
int port;
try {
    port = Integer.parseInt(input);
} catch (NumberFormatException e) {
    System.out.println("Sai định dạng");
}
use(port);
~~~

Đây là đoạn **không độc lập**: giả sử `input` và phương thức `use` tồn tại. Dù `port` được gán trong khối `try`, có hai đường đi:

~~~text
                  int port;  // chưa có giá trị
                           |
                    Integer.parseInt(input)
                       /              \
                Phân tích được       Ném NumberFormatException
                      |                       |
               port = kết quả         catch in thông báo
                      |                       |
                      +-----------+-----------+
                                  |
                               use(port)
~~~

Đường bên phải đi tới `use(port)` mà không gán `port`. Do đó mã **không biên dịch** với lỗi đại loại `variable port might not have been initialized`.

Hai điểm quan trọng:

1. **Phép gán chỉ hoàn tất khi vế phải được đánh giá thành công.** Với `port = Integer.parseInt(input);`, nếu `parseInt` ném lỗi, `port` chưa nhận kết quả từ phép gán đó.
2. **Trình biên dịch kiểm tra mọi đường có thể kết thúc bình thường tại điểm dùng**, không chỉ đường bạn mong muốn xảy ra.

Nếu xử lý lỗi trong `catch` bằng cách in log rồi tiếp tục, bạn vẫn phải bảo đảm biến có giá trị hợp lệ hoặc chuyển hướng điều khiển để không đọc biến chưa gán.

## 3. Cách sửa 1: gán giá trị trong cả `try` và `catch`

Khi nghiệp vụ có giá trị thay thế hợp lệ:

~~~java
static int parseOrFallback(String input) {
    int result;
    try {
        result = Integer.parseInt(input);
    } catch (NumberFormatException e) {
        result = -1;
    }
    return result;
}
~~~

Phương thức này có thể đặt trong một lớp Java 17+. Phân tích:

- Nếu `parseInt` thành công, `result` nhận số nguyên vừa đọc.
- Nếu `parseInt` ném `NumberFormatException`, khối `catch` gán `-1`.
- Trên **mọi đường kết thúc bình thường** của `try/catch`, `result` đã được gán trước `return result;`. Đường nào gặp ngoại lệ khác không được bắt thì kết thúc bất thường và không đi tới lệnh `return`.

Đây là một phương án đúng về mặt biên dịch. Tuy nhiên về nghiệp vụ, `-1` chỉ là fallback an toàn nếu API quy định giá trị đó là giá trị thay thế hoặc dấu hiệu lỗi hợp lệ. Nếu `-1` cũng có thể là dữ liệu bình thường, caller sẽ khó phân biệt thành công với thất bại. Khi đó nên dùng kết quả có cấu trúc, xử lý lỗi ở tầng caller hoặc ném ngoại lệ phù hợp.

### Có thể gán giá trị ngay lúc khai báo

~~~java
static int parseWithDefault(String input) {
    int result = -1;
    try {
        result = Integer.parseInt(input);
    } catch (NumberFormatException e) {
        System.out.println("Sử dụng giá trị mặc định");
    }
    return result;
}
~~~

`result` đã được gán `-1` trước `try`, nên dù parse thất bại vẫn có giá trị để trả về. Cách này có ưu điểm đơn giản, nhưng cần **chọn mặc định có ý nghĩa**; đừng viết `int result = 0;` chỉ để làm compiler im lặng khi `0` có thể che lỗi.

## 4. Cách sửa 2: kết thúc nhánh lỗi bằng `return`

Nếu parsing thất bại thì phương thức có thể trả ngay giá trị thay thế:

~~~java
static int parseOrReturn(String input) {
    int result;
    try {
        result = Integer.parseInt(input);
    } catch (NumberFormatException e) {
        return -1;
    }
    return result;
}
~~~

Đoạn này **biên dịch** dù không gán `result` trong `catch`. Lý do: nhánh `catch` có `return -1;`, nên nhánh đó **không thể tới** `return result;` ở cuối. Mọi đường thực thi còn có thể tới lệnh cuối đều đã gán `result` thành công.

Đây là khác biệt giữa:

- “**Biến phải được gán trên mọi đường đi trong phương thức**” — phát biểu này quá rộng.
- “**Biến phải được gán trên mọi đường có thể thực sự tới điểm đọc**” — đây mới là ý nghĩa cần nhớ.

Không cần gán một giá trị cho biến ở đường đã `return` và không bao giờ đọc biến nữa.

## 5. Cách sửa 3: kết thúc nhánh lỗi bằng `throw`

Khi không có fallback hợp lệ, nhánh xử lý có thể chuyển lỗi lên caller:

~~~java
static int parseOrThrow(String input) {
    int result;
    try {
        result = Integer.parseInt(input);
    } catch (NumberFormatException cause) {
        throw new IllegalArgumentException(
                "Không thể đọc số nguyên: " + input, cause);
    }
    return result;
}
~~~

Khi parse thành công, `result` được gán và trả về. Khi parse thất bại, `catch` ném ngoại lệ mới, **không thể đi tiếp bình thường** đến `return result;`. Vì thế trình biên dịch chấp nhận đoạn mã.

Constructor `IllegalArgumentException(String, Throwable)` giữ `cause` là `NumberFormatException` gốc. Việc giữ nguyên nhân giúp chẩn đoán lỗi thay vì chỉ in log rồi trả một kết quả không rõ ý nghĩa.

Bạn cũng có thể dùng `throw cause;` để ném lại đối tượng đã bắt, nếu hợp đồng của phương thức cho phép và việc giữ loại lỗi gốc có ý nghĩa. Với checked exception, cần tiếp tục tuân thủ quy tắc `catch` hoặc `throws` tương ứng; điều đó độc lập với DA.

## 6. Ví dụ Java 17+ chạy độc lập

Tạo tệp **`DefiniteAssignmentDemo.java`**:

~~~java
public class DefiniteAssignmentDemo {
    public static void main(String[] args) {
        for (String input : new String[] {"18", "oops"}) {
            System.out.println(
                    "fallback(" + input + ")=" + withFallback(input));
            try {
                System.out.println(
                        "propagate(" + input + ")=" + withPropagation(input));
            } catch (IllegalArgumentException e) {
                System.out.println(
                        "propagate(" + input + ") failed: " + e.getMessage());
            }
        }

        System.out.println(
                "returnOnError(oops)=" + returnOnError("oops"));
        System.out.println(
                "finalLocal(oops)=" + useFinalLocal("oops"));
        System.out.println(
                "handled(9)=" + handled("9"));
    }

    static int withFallback(String raw) {
        int result;
        try {
            result = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            result = -1;
        }
        return result;
    }

    static int withPropagation(String raw) {
        int result;
        try {
            result = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "bad integer: " + raw, e);
        }
        return result;
    }

    static int returnOnError(String raw) {
        int result;
        try {
            result = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return -1;
        }
        return result;
    }

    static int useFinalLocal(String raw) {
        final int result = withFallback(raw);
        return result;
    }

    static int handled(String raw) {
        int result;
        try {
            if (raw.isBlank()) {
                return -1;
            }
            result = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            result = 0;
        }
        return result;
    }
}
~~~

Biên dịch, chạy từ thư mục chứa tệp:

~~~bash
javac DefiniteAssignmentDemo.java
java DefiniteAssignmentDemo
~~~

**Stdout:**

~~~text
fallback(18)=18
propagate(18)=18
fallback(oops)=-1
propagate(oops) failed: bad integer: oops
returnOnError(oops)=-1
finalLocal(oops)=-1
handled(9)=9
~~~

### Tại sao chương trình hợp lệ?

| Phương thức | Khi thành công | Khi có lỗi được bắt | Vì sao đọc biến an toàn? |
| --- | --- | --- | --- |
| `withFallback` | Gán `result` từ parse | Gán `result = -1` | Mọi nhánh bình thường đều gán |
| `withPropagation` | Gán rồi trả | `throw` ngoại lệ khác | Nhánh lỗi không đến điểm đọc |
| `returnOnError` | Gán rồi trả ở cuối | `return -1` ngay | Nhánh lỗi không đến điểm đọc |
| `useFinalLocal` | Gọi hàm lấy kết quả rồi gán `final result` | Hàm được gọi đã quyết định fallback | Biến `final` được khởi tạo một lần, trước khi đọc |
| `handled` | Gán qua `parseInt` | Gán `0` nếu parse thất bại; đầu vào trống `return -1` sớm | Mọi đường đến `return result` đều gán |

Các dòng stdout có thứ tự xác định vì chương trình xử lý lần lượt mảng chuỗi `{"18", "oops"}` và chỉ in các kết quả đã bắt. `Integer.parseInt("oops")` ném `NumberFormatException`, một lớp con của `IllegalArgumentException`. Vì `withPropagation` bọc lỗi đó thành `IllegalArgumentException`, nhánh `catch` trong `main` nhận được ngoại lệ như mong đợi.

## 7. Biến khai báo ngoài `try` và biến khai báo bên trong `try`

**Phạm vi** là một vấn đề khác với việc gán chắc chắn.

~~~java
static void scopeExample(String input) {
    try {
        int parsed = Integer.parseInt(input);
        System.out.println(parsed);
    } catch (NumberFormatException e) {
        System.out.println("Sai số");
    }

    // System.out.println(parsed); // Sai: parsed đã ngoài phạm vi.
}
~~~

Ở đây, `parsed` được khai báo bên trong khối `try`. Sau dấu `}` kết thúc khối, tên này không còn trong phạm vi. Kể cả nó chắc chắn được gán ở nhánh thành công, bạn cũng không thể tham chiếu tên `parsed` sau `try/catch`.

Ngược lại:

~~~java
static void scopeAndAssignment(String input) {
    int parsed;
    try {
        parsed = Integer.parseInt(input);
    } catch (NumberFormatException e) {
        parsed = -1;
    }
    System.out.println(parsed);
}
~~~

`parsed` được khai báo ngoài `try` nên vẫn trong phạm vi, đồng thời cả nhánh thành công lẫn nhánh bắt lỗi đều gán giá trị. Có **cả scope lẫn definite assignment** thì mới đọc biến được.

Tham số của `catch`, chẳng hạn `NumberFormatException e`, cũng chỉ có phạm vi trong chính khối `catch`. Đối tượng ngoại lệ do runtime chuyển vào tham số đó; không cần gán `e` thủ công trước khi dùng `e.getMessage()`.

## 8. Nhiều nhánh `catch`: mỗi đường phải được xét

Giả sử một thao tác có thể ném hai loại ngoại lệ khác nhau:

~~~java
static int readOrFallback(String text, boolean unavailable) {
    int result;
    try {
        if (unavailable) {
            throw new IllegalStateException("Nguồn không sẵn sàng");
        }
        result = Integer.parseInt(text);
    } catch (NumberFormatException e) {
        result = -1;
    } catch (IllegalStateException e) {
        result = -2;
    }
    return result;
}
~~~

Phương thức này hợp lệ. Nếu `Integer.parseInt` thành công, `result` nhận số; nếu thất bại do định dạng, nhận `-1`; nếu nguồn không sẵn sàng, nhận `-2`. Mọi đường hoàn tất bình thường của `try/catch` trước `return` đều có giá trị.

Nhưng nếu nhánh thứ hai chỉ ghi log:

~~~java
static int broken(String text, boolean unavailable) {
    int result;
    try {
        if (unavailable) {
            throw new IllegalStateException("Nguồn không sẵn sàng");
        }
        result = Integer.parseInt(text);
    } catch (NumberFormatException e) {
        result = -1;
    } catch (IllegalStateException e) {
        System.out.println("Nguồn lỗi");
    }
    return result; // Không biên dịch: nhánh catch thứ hai chưa gán.
}
~~~

thì `return result` có thể được thực thi sau `catch (IllegalStateException e)` mà chưa có phép gán. Cần gán giá trị có ý nghĩa, `return` hoặc `throw` ở nhánh đó.

**Đừng chỉ kiểm tra nhánh `catch` mà bạn cho là thường gặp.** Quy tắc DA phải xét tất cả đường đến điểm đọc theo phân tích luồng điều khiển của trình biên dịch.

## 9. Ngoại lệ ở vế phải của phép gán

Đây là điểm dễ bị bỏ qua:

~~~java
int value;
try {
    value = Integer.parseInt(input);
} catch (NumberFormatException e) {
    // value vẫn chưa được gán bởi phép gán ở trên.
}
~~~

Java đánh giá vế phải `Integer.parseInt(input)` trước khi hoàn tất gán vào `value`. Nếu lời gọi ném `NumberFormatException`, toàn bộ biểu thức gán không hoàn tất. Không thể suy ra rằng `value` đã “được gán dở” với một giá trị.

Ngay cả với biểu thức phức tạp:

~~~java
int result;
try {
    result = calculate(input);
} catch (IllegalArgumentException e) {
    // Không có bảo đảm result nhận giá trị từ calculate(input).
}
~~~

Đây là đoạn minh họa giả định `calculate` có thể ném `IllegalArgumentException`. Muốn đọc `result` sau đó, phải xử lý đường `catch` hoặc khởi tạo trước. Phân tích DA không dựa vào kết quả **có thể** sinh ra nếu hàm chạy thành công; nó yêu cầu kết quả **chắc chắn** tồn tại trên mọi đường tới điểm đọc.

## 10. Biến `final` cục bộ: chắc chắn đã gán và không gán hai lần

`final` không tạo ngoại lệ đặc biệt cho `try/catch`; nó bổ sung yêu cầu **không được gán lại** sau khi biến đã có giá trị. Một biến `final` cục bộ chưa khởi tạo khi khai báo thường được gọi là *blank final*. Đối với đường thực thi hợp lệ, trình biên dịch còn kiểm tra điều kiện *definite unassignment* (chắc chắn chưa được gán) tại điểm gán để ngăn việc ghi lại.

Cách dễ đọc nhất:

~~~java
static int finalLocalExample(String text) {
    final int result = parseOrFallback(text);
    return result;
}

static int parseOrFallback(String text) {
    try {
        return Integer.parseInt(text);
    } catch (NumberFormatException e) {
        return -1;
    }
}
~~~

Đây là hai phương thức có thể đặt cùng một lớp. `final result` được gán **đúng một lần** từ giá trị mà `parseOrFallback` trả. Nếu hàm được gọi kết thúc bất thường, phép gán không hoàn tất và phương thức gọi cũng không đi tới `return result` theo đường đó.

### Đừng sửa bằng cách gán hai lần cho `final`

~~~java
final int result = 0;
try {
    result = Integer.parseInt(text); // Lỗi: không thể gán lại final.
} catch (NumberFormatException e) {
    System.out.println("Không hợp lệ");
}
~~~

Đây là **phản ví dụ không biên dịch**. Khởi tạo `result = 0` đã gán biến `final`; phép gán trong `try` là không hợp lệ.

Với một *blank final* được gán qua nhiều nhánh `try/catch`, các quy tắc definite assignment **và** definite unassignment có thể khó suy luận hơn, đặc biệt khi exception có thể phát sinh tại các vị trí khác nhau trong `try`. Không mặc định rằng mẫu gán một lần trên giấy sẽ luôn hợp lệ với `final`; với logic phức tạp, ưu tiên **mỗi nhánh trả kết quả** hoặc trích thành một hàm trả giá trị rồi khai báo `final result = ...` bên ngoài. Cách này tránh trạng thái biến nửa chừng và đơn giản hóa luồng điều khiển.

## 11. `finally` có giúp biến tự được gán không?

`finally` chủ yếu dùng cho việc dọn dẹp, hoặc thực hiện hành động cần chạy khi rời `try/catch`. Nó **không tự cấp giá trị mặc định** cho biến cục bộ và không làm trình biên dịch bỏ qua các đường chưa gán.

Ví dụ phương thức hợp lệ:

~~~java
static int parseWithCleanup(String input) {
    int result;
    try {
        result = Integer.parseInt(input);
    } finally {
        System.out.println("Hoàn tất bước đọc");
    }
    return result;
}
~~~

Nếu parse thành công, `result` được gán, `finally` chạy, rồi phương thức trả `result`. Nếu parse ném `NumberFormatException`, `finally` vẫn thường chạy nhưng ngoại lệ tiếp tục truyền ra; đường lỗi **không tới** `return result`. Vì vậy việc đọc `result` ở cuối hợp lệ theo phân tích DA.

Không nên chuyển `System.out.println(result)` vào `finally` trong ví dụ trên khi `result` có thể chưa được gán tại thời điểm `finally` chạy. Trước khi sử dụng biến trong `finally`, cũng phải bảo đảm nó đã được gán trên mọi đường có thể đến chính điểm đọc đó.

Một ví dụ gán mặc định trước khi vào `try`:

~~~java
static int parseWithFinally(String input) {
    int result = -1;
    try {
        result = Integer.parseInt(input);
    } catch (NumberFormatException e) {
        System.out.println("Dùng fallback");
    } finally {
        System.out.println("Xử lý xong");
    }
    return result;
}
~~~

Hàm trên có đường lỗi được xử lý với giá trị mặc định; `finally` không thay thế cho phép khởi tạo `result = -1`.

**Chú ý:** Viết `return` hoặc `throw` mới một cách thiếu cân nhắc trong `finally` có thể thay đổi/che kết quả hay ngoại lệ đang truyền. Đừng sử dụng `finally` như một mẹo để đánh lừa definite assignment; hãy thiết kế đường thành công và thất bại rõ ràng.

## 12. `try-with-resources` và phạm vi biến

Cú pháp try-with-resources mở rộng bài toán bằng tài nguyên được đóng tự động:

~~~java
static String firstLine(String input)
        throws java.io.IOException {
    try (java.io.BufferedReader reader =
                 new java.io.BufferedReader(
                         new java.io.StringReader(input))) {
        return reader.readLine();
    }
}
~~~

`reader` được khởi tạo trong phần khai báo tài nguyên của `try`, và phạm vi của biến tài nguyên gắn với cấu trúc try-with-resources theo luật Java. Phần thân có thể đọc `reader` vì quá trình khởi tạo tài nguyên phải thành công trước khi vào thân `try`.

Nếu constructor hoặc biểu thức khởi tạo tài nguyên thất bại, phần thân có thể không chạy; Java thực hiện các quy tắc đóng tài nguyên liên quan và lan truyền ngoại lệ thích hợp. Điều này không khiến một biến kết quả khai báo ngoài `try` tự động được gán.

Ví dụ khác:

~~~java
String content;
try (java.io.BufferedReader reader =
             new java.io.BufferedReader(
                     new java.io.StringReader(input))) {
    content = reader.readLine();
} catch (java.io.IOException e) {
    content = "fallback";
}
System.out.println(content);
~~~

Đây là **đoạn minh họa trong một phương thức có `input`**. Cả đường đọc thành công và đường `catch` đều gán `content`. Nhưng việc đọc và đóng tài nguyên có thể phát sinh checked exception, và những ngoại lệ không được bắt bởi đoạn `catch` sẽ khiến phương thức kết thúc bất thường. Trường hợp `reader.readLine()` trả `null` khi không có dòng cũng là một **giá trị đã được gán**, dù ứng dụng có thể cần kiểm tra `null` theo nghiệp vụ.

## 13. DA là kiểm tra tĩnh, không phải phép đo giá trị đầu vào

Java xác định definite assignment bằng quy tắc phân tích luồng của ngôn ngữ. Nó không chạy thử chương trình, không chờ dữ liệu thực tế và không có nhiệm vụ chứng minh những giả thiết tùy ý về người dùng.

Ví dụ:

~~~java
int parsed;
try {
    parsed = Integer.parseInt("123");
} catch (NumberFormatException e) {
    System.out.println("Đầu vào sai");
}
System.out.println(parsed); // Không dựa vào giả thiết "123" luôn hợp lệ.
~~~

Đây là **đoạn minh họa không biên dịch**. Dù người đọc biết `"123"` hợp lệ, trình biên dịch vẫn phân tích các đường exception theo quy tắc Java, thay vì chứng minh mọi hành vi runtime của thư viện bằng cách giải thích chuỗi literal.

Ngược lại, `int parsed = 123;` rõ ràng đã gán biến ngay khi khai báo. Phân tích DA cũng xét một số cấu trúc điều khiển cụ thể của ngôn ngữ như `if/else`, `return`, `throw`, biểu thức boolean và vòng lặp theo các quy tắc riêng; không phải mọi điều kiện đều được đánh giá như một định lý logic tùy ý.

**Nếu chương trình biên dịch**, Java bảo vệ bạn khỏi đọc một biến cục bộ chưa được gán trên đường đi được phân tích. Điều đó **không bảo đảm giá trị đã gán là đúng**. Gán `-1` chỉ để vượt compiler có thể giải quyết DA nhưng gây lỗi miền dữ liệu.

## 14. Một hướng viết gọn: trả trực tiếp từ mỗi nhánh

Khi không cần giữ biến trung gian, đây thường là mẫu dễ đọc:

~~~java
static int parseSafe(String text) {
    try {
        return Integer.parseInt(text);
    } catch (NumberFormatException e) {
        return -1;
    }
}
~~~

Tại đây không có biến cục bộ `result` phải được theo dõi qua nhiều đường. Nếu parsing thành công thì trả số, nếu thất bại thì trả fallback. Với checked exception, phương thức tương tự có thể `throws` để chuyển lỗi lên caller; chọn cách phù hợp theo hợp đồng.

Nếu cần phân biệt rõ thành công/thất bại mà `-1` cũng có thể là dữ liệu hợp lệ, hãy cân nhắc kết quả có cấu trúc:

~~~java
record ParseResult(boolean success, int value) {}

static ParseResult parseWithStatus(String text) {
    try {
        return new ParseResult(true, Integer.parseInt(text));
    } catch (NumberFormatException e) {
        return new ParseResult(false, 0);
    }
}
~~~

Đây là hai khai báo Java 17+ có thể đặt chung trong một lớp hoặc ở phạm vi phù hợp. `value = 0` khi `success = false` chỉ là thành phần không mang nghĩa kết quả thành công; caller nên kiểm tra `success` trước khi dùng `value`. Việc trả về từ cả hai nhánh tránh tình huống biến local chưa được gán, đồng thời hợp đồng báo lỗi rõ ràng hơn một sentinel mơ hồ.

## 15. Các lỗi thường gặp

**Lỗi 1 — Tưởng biến cục bộ tự động nhận `0`/`null`.**  
Biến cục bộ không có khởi tạo mặc định để đọc. Field và phần tử mảng mới tạo có quy tắc khác.

**Lỗi 2 — Tưởng khai báo bên ngoài `try` là đủ.**  
Khai báo quyết định phạm vi tên biến. Nó không bảo đảm biến được gán trên mọi đường sau `catch`.

**Lỗi 3 — Chỉ nhìn đường parse thành công.**  
Phải xét đường exception xảy ra **trước khi phép gán hoàn tất**. Khối `catch` chỉ ghi log có thể để biến chưa gán.

**Lỗi 4 — Gán mặc định không có ý nghĩa.**  
`int port = 0;` có thể làm mã biên dịch nhưng gây kết quả sai nếu `0` bị caller hiểu nhầm là cổng hợp lệ. Dùng fallback được hợp đồng chấp nhận hoặc phản ánh thất bại rõ ràng.

**Lỗi 5 — Nhầm `return`/`throw` với đường chạy tiếp.**  
Một nhánh `catch` đã `return` hay `throw` thì không thể đi đến câu lệnh sau `try/catch` theo đường đó. Không cần gán biến chỉ để thỏa một điểm đọc không thể tới.

**Lỗi 6 — Bỏ sót một nhánh `catch`.**  
Nếu nhánh thứ nhất gán biến nhưng nhánh thứ hai chỉ in log và kết thúc bình thường, biến vẫn có thể chưa gán tại điểm đọc.

**Lỗi 7 — Dùng biến ngoài phạm vi.**  
Biến khai báo trong khối `try` không thể được đọc sau khi ra khỏi khối đó, dù trong nhánh thành công nó đã có giá trị. Đây là lỗi scope, khác lỗi DA.

**Lỗi 8 — Dùng `final` rồi cố gán lại.**  
`final int x = 0;` sau đó `x = ...;` luôn sai. Nếu cần giá trị từ các nhánh, nên trả kết quả từ một hàm rồi gán `final` một lần.

**Lỗi 9 — Dựa vào `finally` để làm biến “chắc chắn có giá trị”.**  
`finally` không tự khởi tạo local. Khi đọc biến bên trong `finally` cũng phải xét đường lỗi xảy ra trước phép gán.

**Lỗi 10 — Cho rằng không có `catch` thì mọi đường phải gán biến.**  
Nếu ngoại lệ lan ra ngoài và phương thức kết thúc bất thường, đường đó không đến điểm đọc sau `try`. DA xét các đường có thể tới điểm đọc; không ép đường không thể tới phải gán.

**Lỗi 11 — Xóa lỗi biên dịch bằng cách làm rỗng `catch`.**  
`catch (Exception ignored) {}` không làm phép gán thất bại thành công. Nó có thể còn che mất lỗi gốc và khiến việc chẩn đoán khó hơn.

## 16. Câu hỏi tự kiểm tra

1. Vì sao `int x; System.out.println(x);` không biên dịch, còn đọc một field `int` mới tạo lại hợp lệ?
2. `int value; try { value = Integer.parseInt(text); } catch (NumberFormatException e) { }` có cho phép đọc `value` sau khối không? Tại sao?
3. Có cần gán `value` trong nhánh `catch` nếu nhánh đó kết thúc bằng `return -1;` không?
4. Nếu một nhánh `catch` ném tiếp `IllegalArgumentException`, nó có thể đi tới `return value;` sau `try/catch` không?
5. Trong mẫu `int result = -1; try { result = parseInt(text); } catch (...) {}`, mã biên dịch nhờ điều gì? Rủi ro nghiệp vụ là gì?
6. Biến `parsed` khai báo bên trong `try` có thể dùng ngoài `try/catch` không? Lỗi này thuộc phạm vi hay DA?
7. Một `try` có hai `catch`; nhánh thứ nhất gán `result` còn nhánh thứ hai chỉ log. Có đủ chắc chắn để đọc `result` sau đó không?
8. Vì sao vế phải ném exception khiến phép gán ở vế trái chưa hoàn tất?
9. Biến `final int x = 0;` có được gán lại ở `try` không? Cách nào có thể tránh phân nhánh gán cho `final`?
10. Khi nào có thể đọc biến sau `try/finally` dù thao tác trong `try` có thể ném ngoại lệ trước phép gán?
11. Vì sao việc `Integer.parseInt("123")` hầu như chắc chắn thành công với dữ liệu đó không phải cách đáng tin để bỏ qua phân tích DA?
12. Nếu fallback sentinel cũng là dữ liệu hợp lệ, có phương án kết quả nào rõ hơn?
13. Có thể dùng `return Integer.parseInt(text);` trong `try` và `return -1;` trong `catch` để tránh biến trung gian không?
14. Trong ví dụ chính, vì sao `withPropagation("oops")` không đọc biến chưa gán mặc dù nhánh lỗi không gán `result`?

### Gợi ý đáp án

1. Local không có khởi tạo mặc định, field có giá trị mặc định.
2. Không; parse có thể ném trước phép gán và `catch` không gán.
3. Không; `return` chấm dứt đường lỗi trước điểm đọc.
4. Không; nhánh đó kết thúc bất thường và tìm handler ngoài.
5. Có giá trị mặc định trước `try`; có thể che lỗi hoặc trả giá trị gây hiểu nhầm.
6. Không, vì biến ra ngoài scope.
7. Không, vì đường `catch` thứ hai vẫn có thể đi tới điểm đọc chưa gán.
8. Do vế phải được đánh giá trước khi gán và đã kết thúc bất thường.
9. Không; trả kết quả từ một helper hoặc mọi nhánh có `return` rồi khởi tạo `final` một lần.
10. Khi mọi đường có thể kết thúc bình thường tới điểm đọc đều đã gán; đường exception lan ra ngoài không tới đó.
11. DA là phân tích luồng của trình biên dịch, không dựa vào mong đợi runtime về chuỗi.
12. Trả kiểu kết quả có trạng thái, dùng cách biểu diễn có phân biệt thành công/lỗi hoặc ném ngoại lệ theo hợp đồng.
13. Có; hai nhánh đều trả trực tiếp.
14. Vì nhánh lỗi `throw` ngoại lệ, không thể tới `return result;`.

## 17. Ghi nhớ

**Biến cục bộ phải được gán chắc chắn tại điểm đọc, không nhất thiết phải được gán trong mọi nhánh đã xuất hiện trong mã nguồn.** Với `try/catch`, hãy lần theo mọi đường có thể đến điểm dùng biến: nhánh thành công có phép gán, nhánh `catch` có phép gán hợp lệ, hoặc nhánh thất bại kết thúc bằng `return`/`throw` và không đến điểm đọc. Phân biệt **scope**, **definite assignment**, **giá trị mặc định** và **ngữ nghĩa lỗi nghiệp vụ** sẽ giúp bạn sửa đúng nguyên nhân thay vì thêm một giá trị giả chỉ để mã biên dịch.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="try-catch-007">Ngoại lệ phát sinh ngay trong khối catch</a>

<details>
<summary>Xem chi tiết</summary>

# Ngoại lệ phát sinh ngay trong khối `catch` của Java

Khi một khối `catch` đang xử lý ngoại lệ, **chính các câu lệnh bên trong `catch` vẫn có thể phát sinh ngoại lệ khác**. Lỗi có thể xuất hiện do gọi một API I/O, truy cập tham chiếu `null`, chuyển đổi dữ liệu sai, thao tác ghi log, thực hiện phương án khôi phục thất bại hoặc do lập trình viên chủ động `throw` một ngoại lệ mới.

Điểm quan trọng nhất: **ngoại lệ mới được ném bên trong một `catch` không được xử lý bởi các khối `catch` cùng cấp của chính cấu trúc `try` đó**. Nó được truyền ra ngoài, sau khi thực hiện `finally` liên quan (nếu có), để tìm một bộ xử lý phù hợp ở ngữ cảnh bao ngoài hoặc các lời gọi phía trên. Nếu muốn xử lý ngay lỗi mới, cần một `try/catch` lồng bên trong khối `catch`.

Bài học này phân biệt ngoại lệ gốc, ngoại lệ phát sinh trong handler, việc ném lại, bọc nguyên nhân và tương tác với `finally`; đồng thời hướng dẫn tránh việc một lỗi phụ che mất sự cố ban đầu. Các chương trình hoàn chỉnh chạy với JDK 17 trở lên.

## 1. Một `try` có nhiều `catch` hoạt động như thế nào?

Một cấu trúc thường gặp:

~~~java
try {
    // Thao tác có thể phát sinh lỗi.
} catch (IllegalArgumentException ex) {
    // Xử lý IllegalArgumentException.
} catch (IllegalStateException ex) {
    // Xử lý IllegalStateException phát sinh ở khối try.
} finally {
    // Dọn dẹp cần thiết.
}
~~~

Khi ngoại lệ phát sinh **trong phần thân `try`**, Java tìm `catch` tương thích đầu tiên trong danh sách. Nếu `catch` được chọn chạy xong bình thường, những `catch` còn lại **không chạy tiếp** như các nhánh `if`.

Nếu chính câu lệnh ở nhánh `catch (IllegalArgumentException)` ném `IllegalStateException`, nhánh `catch (IllegalStateException)` nằm bên cạnh **không bắt lỗi mới đó**, vì phạm vi mà cả hai handler bảo vệ là **phần `try` tương ứng**, không phải phần thân của một handler khác.

Đây là điểm dễ nhầm nhất khi đọc một cấu trúc có nhiều `catch`: **các `catch` ngang hàng là những lựa chọn xử lý lỗi của cùng một khối `try`**, không phải một chuỗi tầng bảo vệ lẫn nhau.

Mô hình:

~~~text
try bên trong
    |
    | ném IllegalArgumentException
    v
catch (IllegalArgumentException)  <-- được chọn
    |
    | xử lý lỗi nhưng ném IllegalStateException mới
    v
finally của try bên trong (nếu có)
    |
    v
thoát khỏi toàn bộ try/catch/finally bên trong
    |
    +--> catch của try bao ngoài (nếu tương thích)
    |
    +--> hoặc tiếp tục lan truyền lên phương thức gọi

catch (IllegalStateException) cùng cấp: KHÔNG được chọn cho lỗi từ sibling catch
~~~

Việc tìm handler phụ thuộc vào **phạm vi động của `try` đang bao quanh điểm ném** và kiểu đối tượng `Throwable`. Một `catch` không tự tạo ra một `try` ẩn bảo vệ phần thân của nó.

## 2. Ví dụ Java hoàn chỉnh: ngoại lệ mới từ `catch` đi ra `catch` bao ngoài

Lưu thành tệp `CatchThrowsDemo.java`:

~~~java
public class CatchThrowsDemo {
    static void execute() {
        try {
            try {
                System.out.println("1. Trong try");
                throw new IllegalArgumentException("đầu vào lỗi");
            } catch (IllegalArgumentException original) {
                System.out.println("2. Catch đầu: " + original.getMessage());
                throw new IllegalStateException("khôi phục thất bại", original);
            } catch (IllegalStateException sibling) {
                System.out.println("Không chạy: sibling catch");
            } finally {
                System.out.println("3. Finally bên trong");
            }
        } catch (IllegalStateException ex) {
            System.out.println("4. Catch ngoài: " + ex.getMessage());
            System.out.println("5. Nguyên nhân: " + ex.getCause().getMessage());
        }
        System.out.println("6. Tiếp tục");
    }

    public static void main(String[] args) {
        execute();
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 CatchThrowsDemo.java
java CatchThrowsDemo
~~~

**Stdout chính xác:**

~~~text
1. Trong try
2. Catch đầu: đầu vào lỗi
3. Finally bên trong
4. Catch ngoài: khôi phục thất bại
5. Nguyên nhân: đầu vào lỗi
6. Tiếp tục
~~~

Giải thích thứ tự:

1. Khối `try` trong cùng ném `IllegalArgumentException`. Java chọn nhánh `catch (IllegalArgumentException original)`.
2. Nhánh này in dòng 2, sau đó tạo và ném `IllegalStateException`. Constructor nhận tham số `original` để giữ ngoại lệ gốc làm nguyên nhân.
3. Nhánh `catch (IllegalStateException sibling)` của **cùng cấu trúc** không được dùng để xử lý ngoại lệ từ nhánh trước, nên câu `Không chạy: sibling catch` không bao giờ được in trong lượt này.
4. Trước khi thoát cấu trúc bên trong, `finally` in dòng 3. Nó kết thúc bình thường nên ngoại lệ mới tiếp tục truyền ra.
5. `catch (IllegalStateException ex)` của cấu trúc `try` bao ngoài được chọn. `ex.getCause()` trả về ngoại lệ `IllegalArgumentException` ban đầu.
6. `catch` ngoài hoàn thành bình thường và phương thức tiếp tục in dòng 6.

Hai quan sát cần nhớ: **nhánh sibling không xử lý lỗi từ handler**, và **`finally` nằm trên đường truyền ra ngoài**. Việc dùng constructor `(message, cause)` bảo toàn khả năng truy vết lỗi ban đầu; nó không tự động xảy ra nếu bạn ném một ngoại lệ mới mà không truyền nguyên nhân.

## 3. Bốn tình huống khi xử lý lỗi trong `catch`

Giả sử một thao tác đã ném `original`, rồi chương trình đang ở trong `catch`.

| Hành vi bên trong `catch` | Kết quả chính |
| --- | --- |
| Xử lý hợp lệ và kết thúc bình thường | Lỗi đã được xử lý; luồng có thể tiếp tục sau cấu trúc `try/catch/finally`. |
| `throw original;` | Ném lại **cùng đối tượng** ngoại lệ; tiếp tục lan truyền ra bên ngoài. |
| `throw new IllegalStateException("...", original);` | Tạo ngoại lệ khác và giữ `original` làm `cause`; bên ngoài nhận ngoại lệ mới. |
| Một thao tác bên trong `catch` tự phát sinh ngoại lệ, ví dụ `logger.write(...)` hay `nullValue.length()` | Ngoại lệ từ thao tác đó bắt đầu truyền ra ngoài; lỗi gốc có thể bị che nếu không được lưu làm nguyên nhân hoặc suppressed exception. |

Một khối `catch` cũng có thể `return`, `break` hoặc `continue` trong những ngữ cảnh hợp lệ; như các đường thoát khác, `finally` có thể tác động đến kết quả. Chủ đề này quan trọng khi chính `finally` ném lỗi, được giải thích ở phần 7.

**Khác biệt về đối tượng:**

~~~java
// Đoạn minh họa nằm trong một catch có biến original.
throw original;  // Cùng tham chiếu Throwable.
~~~

~~~java
// Đoạn minh họa nằm trong một catch có biến original.
throw new IllegalStateException("Không thể khôi phục", original);
// Một đối tượng mới, nguyên nhân nối tới đối tượng gốc.
~~~

~~~java
// Đoạn minh họa nằm trong một catch có biến original.
throw new IllegalStateException("Không thể khôi phục");
// Một đối tượng mới nhưng KHÔNG gắn nguyên nhân gốc.
~~~

Cách cuối hợp lệ nhưng có thể làm mất thông tin quan trọng. Chỉ chủ động bỏ `cause` khi có lý do thiết kế rõ ràng, chẳng hạn cần tránh tiết lộ dữ liệu nhạy cảm ở ranh giới công khai; thông thường nên giữ lỗi gốc trong hệ thống chẩn đoán nội bộ.

## 4. Ngoại lệ trong `catch` có thể là lỗi không chủ ý

Không phải mọi ngoại lệ bên trong handler đều do `throw new` tường minh. Các câu lệnh dùng để xử lý lỗi cũng có nguy cơ thất bại:

~~~java
// Đoạn minh họa: một lỗi phụ phát sinh trong catch.
try {
    Integer.parseInt("abc");
} catch (NumberFormatException original) {
    String message = null;
    System.out.println(message.length()); // Ném NullPointerException.
}
~~~

Trong ví dụ này, `NumberFormatException` gốc đã đưa luồng thực thi vào `catch`, nhưng dòng `message.length()` phát sinh `NullPointerException`. Nếu không có `try` khác bảo vệ câu lệnh này, ngoại lệ mới lan ra ngoài. Người theo dõi chỉ thấy `NullPointerException` ở tầng trên có thể nhầm đây là nguyên nhân chính, dù lỗi đầu tiên là chuyển đổi số.

Các nguồn lỗi phụ phổ biến:

- **Ghi log:** logger, formatter, phương thức `toString()` hoặc nơi gửi log có thể thất bại vì bug, tài nguyên hay I/O. Không nên tự xây thông báo lỗi bằng nhiều thao tác dễ phát sinh lỗi khác.
- **Dùng đối tượng lỗi:** gọi `ex.getCause().getMessage()` khi `getCause()` là `null` gây `NullPointerException`. Không được mặc định mọi `Throwable` đều có cause.
- **Khôi phục:** đọc bản sao dự phòng, gọi dịch vụ khác hoặc rollback có thể phát sinh exception.
- **Dọn dẹp thủ công:** đóng stream, giải phóng tài nguyên, lưu audit hoặc gọi `close()` trong handler có thể thất bại.
- **Chuyển đổi dữ liệu để hiển thị lỗi:** phân tích JSON, lấy thông điệp ngoại lệ từ hệ thống ngoài, mã hóa lại dữ liệu hoặc định dạng chuỗi có thể gây lỗi mới.

Trong thiết kế, **logic xử lý lỗi phải đủ đơn giản và có đường thất bại được tính trước**. Nếu một thao tác phụ không được phép che lỗi chính, hãy bảo vệ riêng thao tác đó và giữ lại ngoại lệ gốc để chuyển tiếp.

## 5. Dùng `try/catch` lồng bên trong chính `catch`

Bạn có thể xử lý lỗi phụ ngay tại `catch` bằng cách tạo cấu trúc `try` mới trong phần thân của handler. Khi đó handler bên trong mới có thể bắt ngoại lệ phát sinh từ thao tác phụ.

~~~java
// Mã minh họa nằm trong phương thức có throws IOException phù hợp,
// cùng với hàm audit(Throwable) throws IOException.
try {
    performAction();
} catch (IllegalArgumentException original) {
    try {
        audit(original);
    } catch (IOException auditFailure) {
        original.addSuppressed(auditFailure);
    }
    throw original;
}
~~~

Đây là **đoạn minh họa phụ thuộc các phương thức `performAction`, `audit`**. Ý nghĩa của nó:

1. Ngoại lệ ban đầu `IllegalArgumentException` đã được bắt bởi handler ngoài.
2. `audit(original)` là một thao tác riêng có thể lỗi I/O.
3. `catch (IOException auditFailure)` **nằm trong cấu trúc `try` mới** nên bắt được lỗi audit tại nơi xảy ra.
4. `addSuppressed` gắn lỗi phụ vào `original`, cho phép xem qua `getSuppressed()` nếu đối tượng hỗ trợ suppression.
5. `throw original` giữ nguyên ngoại lệ chính khi tiếp tục truyền ra ngoài.

`addSuppressed` chỉ phù hợp khi bạn muốn duy trì lỗi chính và xem lỗi phụ là sự cố thứ cấp trong lúc xử lý. Không tự thêm chính đối tượng `original` làm suppressed của nó; Java cấm self-suppression. Ngoài ra, lưu ý có thể có các loại `Throwable` tùy chỉnh không bật suppression.

Với thao tác phục hồi có ý nghĩa độc lập, một lựa chọn khác là ném ngoại lệ mới, **kèm `cause` từ lỗi gốc**, để thông báo rõ thất bại ở bước phục hồi. Cách nào đúng phụ thuộc ranh giới trách nhiệm và loại lỗi mà API muốn người gọi xử lý.

## 6. Checked exception được ném từ `catch`: compiler kiểm tra như thế nào?

Trình biên dịch Java kiểm tra các ngoại lệ **checked** có thể thoát khỏi phần thân `catch`, giống như đối với những câu lệnh khác. Nếu handler ném `IOException`, bạn phải bắt ở một cấu trúc `try` phù hợp bao ngoài hoặc khai báo trong `throws` của phương thức/constructor chứa nó.

Chương trình hoàn chỉnh `CheckedCatchThrowsDemo.java`:

~~~java
import java.io.IOException;

public class CheckedCatchThrowsDemo {
    static void convert(String raw) throws IOException {
        try {
            Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            throw new IOException("không chuyển đổi được", ex);
        }
    }

    public static void main(String[] args) {
        try {
            convert("abc");
        } catch (IOException ex) {
            System.out.println("Đã xử lý: " + ex.getMessage());
            System.out.println("Cause: " + ex.getCause().getClass().getSimpleName());
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 CheckedCatchThrowsDemo.java
java CheckedCatchThrowsDemo
~~~

Đầu ra chính xác:

~~~text
Đã xử lý: không chuyển đổi được
Cause: NumberFormatException
~~~

Phân tích:

- `Integer.parseInt("abc")` ném `NumberFormatException`, một unchecked exception.
- `catch (NumberFormatException ex)` bắt lỗi đó, rồi tạo và ném `IOException` **checked** mới.
- Vì `IOException` thoát khỏi phương thức `convert`, chữ ký phải khai báo `throws IOException`, hoặc phải có xử lý phù hợp ở tầng bao ngoài.
- `main` bắt `IOException`, đọc `getCause()` để thấy `NumberFormatException` gốc. Không có nghĩa vụ bắt `NumberFormatException` chỉ vì nó là cause.

Nếu bỏ `throws IOException` khỏi chữ ký `convert` mà không thêm handler phù hợp, chương trình sẽ lỗi biên dịch. Không thể giải quyết bằng cách viết một `catch (IOException)` **ngang hàng bên dưới** `catch (NumberFormatException)`: lỗi I/O được tạo trong handler nên không thuộc khối `try` do `catch` ngang hàng bảo vệ.

Cách hợp lệ khác là đặt toàn bộ `try/catch` bên trong một `try` bao ngoài, rồi `catch (IOException)` ở tầng ngoài. Khi đó ngoại lệ đã phát sinh **bên trong một khối `try` thực sự** thuộc tầng ngoài.

## 7. Vai trò của `finally` khi `catch` ném thêm ngoại lệ

`finally` liên quan đến cấu trúc `try/catch` vẫn được chạy khi khối `catch` kết thúc bất thường do `throw`.

Có hai tình huống khác nhau:

- **`finally` hoàn thành bình thường:** ngoại lệ đã được ném từ `catch` tiếp tục lan truyền ra ngoài.
- **`finally` tự kết thúc bất thường** (ví dụ ném thêm exception hoặc thực hiện `return`): lý do thoát ra của `catch` có thể bị thay thế, làm lỗi vừa ném từ `catch` không còn là ngoại lệ ngoài cùng.

Chương trình `FinallyMasksCatchDemo.java` chứng minh tình huống thứ hai:

~~~java
public class FinallyMasksCatchDemo {
    static void process() {
        try {
            try {
                throw new IllegalArgumentException("lỗi gốc");
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("lỗi từ catch", ex);
            } finally {
                throw new UnsupportedOperationException("lỗi từ finally");
            }
        } catch (RuntimeException ex) {
            System.out.println("Bên ngoài: " + ex.getMessage());
            System.out.println("Cause null: " + (ex.getCause() == null));
        }
    }

    public static void main(String[] args) {
        process();
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 FinallyMasksCatchDemo.java
java FinallyMasksCatchDemo
~~~

Stdout chính xác:

~~~text
Bên ngoài: lỗi từ finally
Cause null: true
~~~

Diễn giải: `IllegalArgumentException("lỗi gốc")` đưa luồng vào `catch`; handler tạo `IllegalStateException("lỗi từ catch", ex)`. Nhưng `finally` lại ném `UnsupportedOperationException`. Trong ví dụ này, lỗi cuối được `catch` ngoài nhận là lỗi từ `finally`; nó không được tạo với `cause`, nên `getCause() == null`. Hai lỗi trước bị che khỏi chuỗi nguyên nhân ngoài cùng.

**Không nên dùng mẫu này trong mã thực tế.** Mục tiêu chỉ là quan sát quy tắc thứ tự thoát. Tránh `throw` hoặc `return` tùy tiện trong `finally`. Nếu công việc dọn dẹp có thể lỗi, hãy dùng cơ chế quản lý tài nguyên hoặc xử lý riêng để giữ lại lỗi chính.

Trường hợp `finally` chứa lệnh `return` cũng có thể che ngoại lệ đang truyền. Do đó `return` trong `finally` là một lỗi thiết kế thường gặp, kể cả khi không có `throw` rõ ràng ở đó.

## 8. `try-with-resources` khác gì với ngoại lệ phát sinh trong `catch`?

`try-with-resources` dùng cho các tài nguyên `AutoCloseable` và xử lý đặc biệt lỗi khi đóng tài nguyên. Nếu cả **thân `try`** và **`close()`** cùng ném ngoại lệ trong quá trình rời `try`, lỗi đóng thường được thêm vào `getSuppressed()` của ngoại lệ chính, giúp giữ lại dấu vết cả hai.

Tuy nhiên, với một cấu trúc như:

~~~java
// Đoạn minh họa có khai báo Resource và ErrorType phù hợp.
try (Resource resource = openResource()) {
    doWork(resource);
} catch (ErrorType original) {
    throw new IllegalStateException("Xử lý lỗi cũng thất bại", original);
}
~~~

Tài nguyên trong `try-with-resources` đã được đóng theo các quy tắc của cấu trúc **trước khi phần `catch` tương ứng xử lý ngoại lệ**. Nếu câu lệnh trong `catch` tạo thêm `IllegalStateException`, Java không tự động gắn `ErrorType original` thành suppressed của lỗi mới; ở đây programmer giữ nguyên nhân bằng tham số `original` trong constructor.

Vì thế cần phân biệt:

- **Suppressed do cơ chế đóng tài nguyên:** thể hiện các lỗi thứ cấp được ghi khi tài nguyên đóng trong `try-with-resources`.
- **`Cause` do code chủ động bọc lỗi:** biểu thị một ngoại lệ là nguyên nhân dẫn tới ngoại lệ khác, ví dụ `new IllegalStateException("...", original)`.
- **Ngoại lệ do handler vô tình ném:** có thể che nguyên nhân gốc nếu không được thiết kế đường xử lý cẩn thận.

`Throwable.getCause()` và `Throwable.getSuppressed()` phục vụ hai mục đích khác nhau. Khi debug một luồng lỗi phức tạp, cần kiểm tra cả hai.

## 9. Ném lại cùng ngoại lệ và giữ stack trace

Một nhánh `catch` có thể ném lại đối tượng đang được bắt:

~~~java
// Đoạn minh họa bên trong phương thức có throws IOException.
try {
    readFromDisk();
} catch (IOException ex) {
    System.err.println("Không đọc được tài nguyên");
    throw ex;
}
~~~

`throw ex;` **không tạo đối tượng mới**, do đó về thông thường không chụp lại stack trace ở vị trí `throw ex`. Stack trace của `Throwable` thường được ghi nhận lúc đối tượng được tạo hoặc tại lần gọi `fillInStackTrace()`; nếu chỉ ném lại tham chiếu cũ thì dấu vết ban đầu vẫn có thể được bảo toàn.

Trong khi đó:

~~~java
// Đoạn minh họa có IOException ex ở catch.
throw new IllegalStateException("Lỗi đọc dữ liệu ứng dụng", ex);
~~~

là tạo ngoại lệ mới tại tầng xử lý; ngoại lệ mới có stack trace riêng, còn `ex` được giữ làm cause. Khi hiển thị đầy đủ stack trace, thường có chuỗi `Caused by:` để lần về lỗi nguồn.

Cần cân nhắc giữa hai cách:

- **`throw ex`:** giữ loại ngoại lệ và danh tính đối tượng; phù hợp khi tầng hiện tại chưa có cách phục hồi và chưa cần đổi hợp đồng.
- **Bọc kèm cause:** hữu ích khi tầng hiện tại muốn cung cấp ngữ cảnh cấp cao hoặc chuyển đổi loại lỗi. Không bọc nhiều tầng chỉ để lặp lại cùng một thông điệp.
- **Không ném lại:** chỉ phù hợp khi `catch` đã thật sự xử lý, có kết quả thay thế hợp lệ hoặc chủ động chuyển sang một đường lỗi khác với tài liệu rõ ràng.

Khi `ex` là checked exception, hãy tuân thủ các quy tắc compiler về ngoại lệ được ném lại và chữ ký `throws`. Java có phân tích **precise rethrow** trong một số dạng `catch`, nhưng điều đó không biến unchecked thành checked hoặc thay đổi ý nghĩa của `throw ex;`.

## 10. Các chiến lược giữ lỗi gốc khi handler cũng thất bại

Hãy đặt câu hỏi: **lỗi nào phản ánh thất bại chính mà caller cần biết?** Sau đó chọn cách truyền phù hợp.

### Trường hợp A: lỗi xử lý mới cần được công bố, lỗi gốc là nguyên nhân

~~~java
// Đoạn minh họa trong một catch có biến original.
throw new IllegalStateException("Không thể khôi phục trạng thái", original);
~~~

Người gọi nhận một ngoại lệ mới và có thể dùng `getCause()` để đi đến nguồn lỗi. Phù hợp nếu nỗ lực khôi phục là bước quyết định thất bại và tầng hiện tại có trách nhiệm cung cấp ngữ cảnh mới.

### Trường hợp B: lỗi gốc là chính, lỗi audit/cleanup chỉ là phụ

~~~java
// Đoạn minh họa trong một catch có biến original, auditingFailure.
original.addSuppressed(auditingFailure);
throw original;
~~~

Cách này giữ nguyên lỗi gốc và lưu lỗi phụ. Nên kiểm tra các quy định về self-suppression và về việc dùng suppressed exceptions của lớp `Throwable` cụ thể.

### Trường hợp C: xử lý hoàn tất thật sự

~~~java
// Đoạn minh họa trong một catch có fallback phù hợp.
String cached = readValidCachedValue();
return cached;
~~~

Khi một giá trị dự phòng đáng tin được tính thành công và hợp đồng cho phép, handler có thể hoàn thành bình thường. Nếu `readValidCachedValue()` lại ném ngoại lệ, nó vẫn cần được xử lý hoặc truyền tiếp; việc đặt lời gọi trong `catch` không miễn trừ nguy cơ lỗi.

### Trường hợp D: chỉ cần báo lỗi, không có thao tác dễ thất bại

~~~java
// Đoạn minh họa bên trong catch với biến ex.
System.err.println("Không hoàn thành thao tác: " + ex.getMessage());
throw ex;
~~~

Đây là cách diễn giải cơ bản. Trong hệ thống thực, logger có thể thất bại hoặc log trùng qua nhiều tầng; nên dùng logger được quản lý đúng cách và lựa chọn một ranh giới chịu trách nhiệm ghi log. Tránh giả định mọi công việc trong handler là tuyệt đối an toàn.

Không có chiến lược bắt buộc áp dụng cho mọi hệ thống. Điều quan trọng là giữ được **ngữ nghĩa lỗi, khả năng kiểm tra nguyên nhân và hành vi caller mong đợi**.

## 11. Lưu ý về `catch` nhiều nhánh và kiểu ngoại lệ

Mỗi `catch` được chọn dựa vào kiểu runtime của đối tượng ngoại lệ phát sinh **trong phần được bảo vệ**. Một số quy tắc cần nhớ:

1. `catch (RuntimeException ex)` bắt được `IllegalArgumentException`, `IllegalStateException` và các lớp con phù hợp; nhưng không bắt `Error`.
2. Khi có nhiều `catch`, đặt kiểu con trước kiểu cha; `catch (Exception)` đứng trước `catch (IOException)` sẽ làm nhánh sau không thể tới và gây lỗi biên dịch.
3. Multi-catch như `catch (IOException | SQLException ex)` dùng để bắt một trong hai loại **từ cùng phần `try`**. Nó không giúp bắt ngoại lệ mới xuất hiện trong chính phần thân multi-catch.
4. Có thể dùng `catch (Exception ex)` nhưng không nên sử dụng quá rộng rồi bỏ trống, vì có thể vô tình biến lỗi nghiêm trọng thành thành công giả.
5. Nếu nhánh `catch` ném một kiểu checked exception mới, compiler kiểm tra khả năng thoát theo kiểu đó; `catch` sibling không làm ngoại lệ mới tự được xử lý.

Đặc biệt, đoạn sau **không hoạt động theo cách nhiều người tưởng**:

~~~java
// Đoạn minh họa có perform() phù hợp với ví dụ.
try {
    perform();
} catch (IllegalArgumentException ex) {
    throw new IllegalStateException("Handler failed", ex);
} catch (IllegalStateException ex) {
    System.out.println("Không bắt được lỗi từ catch trước!");
}
~~~

Nếu `perform()` ném `IllegalArgumentException`, handler đầu được chọn và ném `IllegalStateException` mới. Handler thứ hai không bắt ngoại lệ mới đó. Nhưng nếu **chính `perform()` ném `IllegalStateException`**, handler thứ hai có thể được chọn bình thường. Cùng là `IllegalStateException`, song **điểm ném và phạm vi `try`** quyết định nhánh nào được sử dụng.

## 12. Kiểm tra thực tế khi một `catch` không hoạt động như mong đợi

Khi xem log hoặc debug một ngoại lệ xuất hiện trong handler:

1. Xác định **ngoại lệ gốc nào dẫn tới nhánh `catch` đầu tiên**. Quan sát kiểu, thông điệp và stack trace ban đầu.
2. Đọc từng câu lệnh trong phần thân handler, đặc biệt những lời gọi ghi log, truy cập `getCause()`, đọc dữ liệu dự phòng và cleanup.
3. Xác định **dòng ném ngoại lệ thứ hai** và kiểu thực tế của nó. Đừng tự giả định nó cùng kiểu với lỗi gốc.
4. Đánh dấu `try` nào **thực sự bao quanh dòng ném mới**. Những `catch` sibling của cấu trúc hiện tại không phải handler cho lỗi phát sinh bên trong `catch`.
5. Kiểm tra `finally`: nó chạy theo đường nào và có ném/return đè lỗi đang được truyền không?
6. Ở handler bao ngoài, kiểm tra `getCause()` và `getSuppressed()`; nếu đều trống, xem code có làm mất lỗi gốc khi tạo ngoại lệ mới không.
7. Chọn chỗ xử lý hoặc chuyển tiếp có chủ đích, viết bài kiểm thử mô phỏng cả **thất bại ban đầu** và **thất bại của logic xử lý**.

Ví dụ kiểm thử đáng làm: giả lập API chính ném `IOException`, sau đó giả lập audit cũng ném `IOException`. Hãy xác minh rõ **loại lỗi mà caller nhận**, **cause/suppressed** và **trạng thái còn lại của tài nguyên**. Một bài kiểm thử chỉ chạy nhánh thành công sẽ bỏ sót các lỗi được che trong handler.

## 13. Những lỗi thường gặp

| Sai lầm | Hệ quả | Cách sửa |
| --- | --- | --- |
| Viết `catch (B)` cùng cấp rồi mong bắt B ném từ `catch (A)` | B tiếp tục lan ra ngoài, handler cùng cấp không được chọn. | Đặt `try/catch` lồng ngay trong handler hoặc dùng `try` bao ngoài. |
| Bọc ngoại lệ mới mà không giữ `cause` | Dấu vết lỗi gốc dễ bị mất. | Dùng constructor `(message, cause)` hoặc cơ chế lưu lỗi phụ phù hợp. |
| Gọi `ex.getCause().getMessage()` vô điều kiện | Có thể phát sinh `NullPointerException` ngay khi xử lý lỗi. | Kiểm tra `getCause()` khác `null` trước khi dùng. |
| Dùng `return` hoặc `throw` trong `finally` | Có thể che ngoại lệ từ `try`/`catch`. | Để `finally` dọn dẹp và hoàn thành bình thường khi có thể. |
| Cho rằng `catch` được chọn đã bảo đảm xử lý hoàn tất | Handler vẫn có thể thất bại giữa chừng. | Thiết kế và kiểm thử đường lỗi của handler. |
| Thêm một ngoại lệ checked mới trong `catch` nhưng bỏ khai báo `throws` | Lỗi biên dịch về checked exception. | Bắt trong `try` lồng, bọc hợp lý hoặc khai báo lên tầng gọi. |
| Ghi log cùng lỗi ở mọi tầng, đồng thời ném lại | Log trùng lặp, khó xác định số sự cố thật. | Log ở ranh giới chịu trách nhiệm hoặc chỉ thêm ngữ cảnh khi chuyển tầng. |
| Nuốt lỗi phụ mà không ghi nhận khi nó ảnh hưởng kết quả | Khó chẩn đoán vì mất dấu vết quá trình khôi phục. | Giữ lỗi phụ trong suppressed, cause hoặc cơ chế giám sát phù hợp. |
| Coi `finally` và `try-with-resources` hoàn toàn tương đương | Sai khác về cách bảo toàn lỗi đóng tài nguyên. | Dùng `try-with-resources` cho `AutoCloseable` khi thích hợp. |
| Hiểu `throw ex` là tạo lại exception tại handler | Có thể đọc sai stack trace. | Phân biệt ném lại tham chiếu và `throw new` tạo đối tượng mới. |

## 14. Tự kiểm tra

1. **Một `try` có `catch (IllegalArgumentException)` rồi `catch (IllegalStateException)`. Nếu nhánh đầu ném `IllegalStateException`, nhánh thứ hai có bắt không?** — Không. Nó chỉ có thể bắt `IllegalStateException` phát sinh ở phạm vi `try` đang bảo vệ, không phải từ sibling catch.
2. **Muốn bắt lỗi audit ngay khi đang ở `catch` phải làm gì?** — Đặt một `try/catch` mới lồng trong handler để bao quanh lời gọi audit.
3. **`catch` ném `IOException` checked, còn phương thức không có `throws IOException` và không có handler bao ngoài. Chương trình biên dịch không?** — Không; phải bắt hoặc khai báo checked exception mới.
4. **`throw original;` có tạo đối tượng khác không?** — Không, vẫn là chính tham chiếu `original`.
5. **`throw new IllegalStateException("fail", original);` cho phép lần theo lỗi gốc như thế nào?** — Qua `getCause()` và phần `Caused by:` của stack trace.
6. **Nếu `finally` ném ngoại lệ khác trong lúc handler đang ném lỗi, ngoại lệ nào có thể ra ngoài?** — Ngoại lệ mới từ `finally` có thể thay thế ngoại lệ đang chờ truyền ra.
7. **`getSuppressed()` và `getCause()` giống nhau không?** — Không. Suppressed chứa các lỗi phụ được ghi nhận; cause mô tả quan hệ nguyên nhân của ngoại lệ.
8. **`catch` ghi log rồi bị `NullPointerException` trong formatter, lỗi gốc có tự trở thành cause không?** — Không. Nếu không được giữ bằng code, lỗi gốc có thể mất khỏi chuỗi nguyên nhân.
9. **Tại sao cần thử cả nhánh lỗi của handler?** — Vì tác vụ khôi phục, audit và dọn dẹp cũng có thể thất bại và thay đổi ngoại lệ mà caller nhận được.
10. **Sau khi `catch` ném ngoại lệ mới và `finally` hoàn thành bình thường, chương trình có thực hiện dòng ngay sau cấu trúc đó không?** — Không ở đường thực thi này nếu ngoại lệ vẫn chưa được xử lý; nó tiếp tục truyền tới bộ xử lý bao ngoài hoặc lời gọi phía trên.

## Tài liệu tham khảo

- [Java Language Specification 17, §14.20.1 — Execution of try-catch](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.1): cách chọn và thực thi một nhánh `catch`.
- [Java Language Specification 17, §14.20.2 — Execution of try-finally and try-catch-finally](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.2): quy tắc `finally` trong đường kết thúc bất thường.
- [Java Language Specification 17, §11.2 — Compile-Time Checking of Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.2): checked exception phát sinh trong các câu lệnh của phương thức.
- [Java Language Specification 17, §11.3 — Run-Time Handling of an Exception](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.3): tìm handler theo ngữ cảnh thực thi.
- [Java SE 17 API — Throwable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html): `getCause()`, `getSuppressed()`, `addSuppressed()` và stack trace.

**Điểm cốt lõi:** một `catch` đang xử lý lỗi không có nghĩa mọi câu lệnh trong đó đều an toàn. Nếu handler ném ngoại lệ khác, ngoại lệ ấy được xử lý theo các `try` bao quanh **điểm ném mới**; các `catch` cùng cấp không bắt hộ. Thiết kế handler đơn giản, giữ lỗi gốc qua cause/suppressed khi cần và chú ý `finally` để chẩn đoán chính xác.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="try-catch-008">Try-catch lồng nhau và ranh giới xử lý</a>

<details>
<summary>Xem chi tiết</summary>

# Try-catch lồng nhau và ranh giới xử lý ngoại lệ trong Java

Trong Java, `try-catch` có thể nằm **bên trong một `try` khác**. Cách tổ chức này giúp phân chia trách nhiệm: tầng trong xử lý lỗi mà nó **biết cách khôi phục**, còn tầng ngoài xử lý lỗi **vượt khỏi khả năng** của tầng trong hoặc quyết định kết quả của toàn bộ thao tác.

Điều khó không phải viết nhiều dấu ngoặc nhọn, mà là trả lời đúng ba câu hỏi:

1. **Ngoại lệ được ném ở đâu?** Nó đang nằm trong vùng `try` nào và đi qua những lời gọi nào?
2. **Handler nào được xét trước?** Một `catch` ở vùng trong có thể bắt lỗi trước khi handler ở vùng ngoài nhìn thấy, ngay cả khi handler ngoài có kiểu cụ thể hơn.
3. **Tầng nào có trách nhiệm xử lý?** Sau khi bắt lỗi, nên tiếp tục, ném lại, chuyển sang ngoại lệ mới có `cause` hay dừng thao tác?

**Quy tắc nền tảng:** Java tìm handler phù hợp theo **vùng xử lý đang hoạt động từ trong ra ngoài**. Trong cùng một cấu trúc `try`, các nhánh `catch` được xét **theo thứ tự viết từ trên xuống**. Handler đã xử lý xong mà không ném lại sẽ ngăn ngoại lệ đó lan ra tầng ngoài.

## 1. Cú pháp của `try-catch` lồng nhau

Cấu trúc tổng quát:

```java
try {
    // Công việc của phạm vi bên ngoài.
    try {
        // Công việc cần một chính sách xử lý cục bộ.
    } catch (IllegalArgumentException ex) {
        // Xử lý loại lỗi trong phạm vi bên trong.
    } finally {
        // Kết thúc/dọn dẹp cho phạm vi bên trong.
    }

    // Chỉ chạy nếu cấu trúc bên trong hoàn tất bình thường.
} catch (IllegalStateException ex) {
    // Nhận lỗi từ phần try ngoài, kể cả lỗi lan ra từ bên trong.
} finally {
    // Kết thúc/dọn dẹp cho phạm vi ngoài.
}
```

Đây là **khung cú pháp minh họa**, không phải chương trình độc lập. Có thể đặt `try-catch` trong phương thức, vòng lặp, một nhánh điều kiện hoặc thậm chí trong một nhánh `catch` khác.

Ở ví dụ:

- `try` ngoài đang thực thi cả cấu trúc `try-catch-finally` trong như một phần nội dung của nó.
- `catch (IllegalArgumentException)` trong được xét cho các lỗi thoát khỏi **phần thân `try` trong**.
- Nếu lỗi trong không khớp `catch` trong, hoặc khối `catch` trong **ném một lỗi mới**, lỗi có thể đi ra ngoài và được `catch` ngoài bắt nếu tương thích.
- `finally` trong thường chạy khi rời `try` trong; `finally` ngoài thường chạy khi rời `try` ngoài. Khi lỗi lan từ trong ra ngoài, các hoạt động dọn dẹp xảy ra theo phạm vi đang thoát.
- Những lệnh còn lại trong phần `try` mà lỗi đã làm gián đoạn **không tự động tiếp tục** sau khi handler tầng ngoài xử lý xong.

### Sơ đồ đường đi

```text
try ngoài
│
├─ thao tác trước
│
├─ try trong
│  ├─ lệnh gây lỗi X
│  ├─ catch trong khớp X?
│  │  ├─ Có  → xử lý xong → finally trong → tiếp tục trong try ngoài
│  │  └─ Không → finally trong → X đi ra try ngoài
│  └─ nếu catch trong ném Y → finally trong → Y đi ra try ngoài
│
├─ lệnh sau try trong, chỉ chạy nếu phần trong hoàn tất bình thường
│
├─ catch ngoài phù hợp với X/Y (nếu X/Y thoát khỏi try ngoài)
│
└─ finally ngoài
   ↓
sau toàn bộ cấu trúc, nếu nó hoàn tất bình thường
```

Một điểm dễ nhầm: **“handler gần nhất” không có nghĩa “handler khai báo kiểu con cụ thể nhất trên toàn chương trình”**. Điều quan trọng trước hết là handler nằm ở **vùng đang bảo vệ đường thực thi**.

## 2. Tình huống A: handler bên trong xử lý xong

Xem đoạn mã nằm trong thân một phương thức:

```java
try {
    try {
        throw new NumberFormatException("bad number");
    } catch (IllegalArgumentException ex) {
        System.out.println("inner: handled");
    }

    System.out.println("outer: after inner");
} catch (NumberFormatException ex) {
    System.out.println("outer: number-format");
}
```

**Stdout:**

```text
inner: handled
outer: after inner
```

`NumberFormatException` kế thừa `IllegalArgumentException`, nên `catch (IllegalArgumentException)` trong bắt được lỗi. Dù `catch (NumberFormatException)` ngoài cụ thể hơn, nó **không được chạy**, vì ngoại lệ đã được xử lý trong.

Đường đi: ném lỗi → handler trong → hoàn thành cấu trúc trong → tiếp tục câu lệnh sau cấu trúc trong → kết thúc `try` ngoài bình thường.

Khi nào hợp lý? Tầng trong có đủ khả năng phục hồi: chẳng hạn nó phát hiện một dòng nhập liệu không hợp lệ, bỏ qua dòng đó theo hợp đồng cho phép, và tầng ngoài tiếp tục xử lý những dòng còn lại.

**Chú ý:** “không ném lại” chỉ hợp lý nếu handler thật sự đã đưa chương trình về trạng thái chấp nhận được. Một `catch` rỗng làm tầng ngoài tưởng mọi thứ thành công có thể là lỗi thiết kế.

## 3. Tình huống B: handler trong không khớp, lỗi lan ra ngoài

Ví dụ:

```java
try {
    try {
        System.out.println("inner: begin");
        throw new IllegalStateException("repository unavailable");
    } catch (NumberFormatException ex) {
        System.out.println("inner: invalid number");
    } finally {
        System.out.println("inner: cleanup");
    }

    System.out.println("outer: next operation");
} catch (IllegalStateException ex) {
    System.out.println("outer: " + ex.getMessage());
}
System.out.println("after: continue");
```

**Stdout:**

```text
inner: begin
inner: cleanup
outer: repository unavailable
after: continue
```

Phân tích:

1. `try` trong ném `IllegalStateException`.
2. Handler `NumberFormatException` trong không khớp.
3. `finally` trong chạy và in `inner: cleanup`.
4. Lỗi lan ra `try` ngoài; dòng `outer: next operation` bị bỏ qua.
5. `catch (IllegalStateException)` ngoài nhận lỗi và xử lý.
6. Nếu handler ngoài kết thúc bình thường, chương trình tiếp tục tại `after: continue`.

Không nên cho rằng khi lỗi đã được bắt ngoài, chương trình “quay lại” để chạy dòng `outer: next operation`. Vùng `try` ngoài đã hoàn tất bất thường tại vị trí cấu trúc trong.

## 4. Tình huống C: handler trong bắt lỗi rồi ném lại cùng đối tượng

```java
try {
    try {
        throw new IllegalArgumentException("quantity <= 0");
    } catch (IllegalArgumentException ex) {
        System.out.println("inner: record context");
        throw ex;
    }
} catch (IllegalArgumentException ex) {
    System.out.println("outer: " + ex.getMessage());
}
```

**Stdout:**

```text
inner: record context
outer: quantity <= 0
```

`throw ex;` ném lại **chính đối tượng ngoại lệ** đã bắt, do đó giữ kiểu runtime và `cause` sẵn có của nó. Handler ngoài nhìn thấy ngoại lệ vì handler trong **không kết thúc bình thường**.

Đây có thể là lựa chọn hợp lý khi phạm vi trong cần ghi nhận một thông tin hữu ích, thực hiện dọn dẹp không thể chuyển sang `finally`/`try-with-resources`, hoặc quyết định rằng việc phục hồi phải do tầng khác đảm nhận.

Tuy nhiên, một handler chỉ viết:

```java
catch (IllegalArgumentException ex) {
    throw ex;
}
```

thường là **dư thừa** nếu nó không bổ sung hành động hay hợp đồng nào. Không cần tạo thêm cấp `try-catch` chỉ để ngoại lệ đi qua.

## 5. Tình huống D: handler trong chuyển sang ngoại lệ mới có nguyên nhân

Có lúc tầng trong biết lỗi kỹ thuật, còn tầng ngoài cần lỗi ở cấp nghiệp vụ/điều phối:

```java
try {
    try {
        Integer.parseInt("abc");
    } catch (NumberFormatException cause) {
        throw new IllegalStateException(
                "cannot prepare order quantity", cause);
    }
} catch (IllegalStateException ex) {
    System.out.println(ex.getMessage());
    System.out.println(ex.getCause().getClass().getSimpleName());
}
```

**Stdout:**

```text
cannot prepare order quantity
NumberFormatException
```

Handler trong tạo một **đối tượng mới** `IllegalStateException` và gắn `NumberFormatException` ban đầu làm `cause`. Handler ngoài bắt ngoại lệ **mới** theo kiểu `IllegalStateException`, chứ không tự nhìn xuyên qua `cause` để bắt theo kiểu `NumberFormatException`.

Phân biệt:

| Thao tác trong handler | Ngoại lệ đi ra tầng ngoài | Thông tin giữ lại |
| --- | --- | --- |
| Xử lý và không ném lại | Không có ngoại lệ đó đi ra | Có thể trả trạng thái hợp lệ |
| `throw ex;` | Cùng đối tượng `ex` | Kiểu và cause gốc |
| `throw new X("context", ex);` | Đối tượng mới kiểu `X` | Cause liên kết tới `ex` |
| `throw new X("context");` | Đối tượng mới kiểu `X` | Có nguy cơ mất liên hệ với nguyên nhân ban đầu |

Chỉ đổi loại ngoại lệ khi **hợp đồng tầng trên** hoặc ngữ cảnh cần điều đó. Không nên bọc lặp lại ở mọi tầng thành nhiều lớp “operation failed” vô nghĩa.

## 6. Ví dụ Java 17+ hoàn chỉnh: bốn đường đi trong nested `try-catch`

Chương trình này chạy độc lập, không cần tệp hay kết nối bên ngoài. Lưu tên **`NestedTryCatchDemo.java`**:

```java
public class NestedTryCatchDemo {

    static void run(String mode) {
        System.out.println("case=" + mode);

        try {
            System.out.println("outer: enter");

            try {
                System.out.println("inner: enter");

                if ("format".equals(mode)) {
                    throw new NumberFormatException("bad integer");
                }
                if ("state".equals(mode)) {
                    throw new IllegalStateException("closed");
                }
                if ("convert".equals(mode)) {
                    throw new IllegalArgumentException("raw");
                }

                System.out.println("inner: success");

            } catch (NumberFormatException ex) {
                System.out.println("inner: format handled");

            } catch (IllegalArgumentException ex) {
                System.out.println("inner: wrapping argument");
                throw new IllegalStateException(
                        "invalid request", ex);

            } finally {
                System.out.println("inner: finally");
            }

            System.out.println("outer: after inner");

        } catch (IllegalStateException ex) {
            System.out.println(
                    "outer: state=" + ex.getMessage());
            String causeName = ex.getCause() == null
                    ? "none"
                    : ex.getCause().getClass().getSimpleName();
            System.out.println("outer: cause=" + causeName);

        } finally {
            System.out.println("outer: finally");
        }

        System.out.println("after: complete");
    }

    public static void main(String[] args) {
        run("ok");
        run("format");
        run("state");
        run("convert");
    }
}
```

Biên dịch và chạy bằng **JDK 17 trở lên**:

```bash
javac -encoding UTF-8 NestedTryCatchDemo.java
java NestedTryCatchDemo
```

**Stdout chính xác:**

```text
case=ok
outer: enter
inner: enter
inner: success
inner: finally
outer: after inner
outer: finally
after: complete
case=format
outer: enter
inner: enter
inner: format handled
inner: finally
outer: after inner
outer: finally
after: complete
case=state
outer: enter
inner: enter
inner: finally
outer: state=closed
outer: cause=none
outer: finally
after: complete
case=convert
outer: enter
inner: enter
inner: wrapping argument
inner: finally
outer: state=invalid request
outer: cause=IllegalArgumentException
outer: finally
after: complete
```

### Phân tích theo từng chế độ

**`ok` — không có lỗi:** chạy câu `inner: success`; không handler nào chạy; `finally` trong chạy; quay lại phần `try` ngoài để in `outer: after inner`; cuối cùng `finally` ngoài và dòng `after: complete`.

**`format` — handler trong xử lý được:** ném `NumberFormatException`, được `catch (NumberFormatException)` trong bắt. Nó **không lan đến `catch` ngoài**. Sau `finally` trong, phần `try` ngoài tiếp tục in `outer: after inner`.

**`state` — lỗi không được handler trong bắt:** ném `IllegalStateException`. Hai nhánh trong chỉ dành cho `NumberFormatException` và `IllegalArgumentException`, vì vậy không khớp. `finally` trong chạy, rồi `catch (IllegalStateException)` ngoài nhận lỗi. Do không có `cause`, in `none`. Dòng `outer: after inner` không chạy.

**`convert` — handler trong chuyển lỗi:** ném `IllegalArgumentException("raw")`; handler trong bắt rồi ném `IllegalStateException("invalid request", ex)`. `finally` trong chạy; handler ngoài bắt lỗi mới và in `IllegalArgumentException` từ `getCause()`. Dòng `outer: after inner` không chạy, vì cấu trúc trong kết thúc bất thường.

Tất cả bốn trường hợp đều in `outer: finally` và `after: complete` trong chương trình này, vì handler ngoài giải quyết được các loại lỗi hiện có và không ném lỗi mới.

### Bảng quyết định ngắn

| Mode | Lỗi xuất hiện trong | Handler xử lý cuối cùng | Phần sau cấu trúc trong thuộc `try` ngoài có chạy? |
| --- | --- | --- | --- |
| `ok` | Không có | Không áp dụng | Có |
| `format` | `try` trong | `catch NumberFormatException` trong | Có |
| `state` | `try` trong | `catch IllegalStateException` ngoài | Không |
| `convert` | `try` trong, sau đó wrapper từ handler trong | `catch IllegalStateException` ngoài | Không |

**Ý nghĩa của ranh giới:** nơi bắt lỗi nên tương ứng với **quyết định có thể thực hiện**. Trường hợp `format` có handler trong vì xử lý cục bộ được; `state` và `convert` trở thành trách nhiệm tầng ngoài.

## 7. Những ngoại lệ ném từ `catch` không được các `catch` anh em bắt

Một sai lầm phổ biến là tin rằng handler sau có thể xử lý lỗi do handler trước ném:

```java
try {
    throw new IllegalArgumentException("bad data");
} catch (IllegalArgumentException ex) {
    throw new IllegalStateException("conversion failed", ex);
} catch (IllegalStateException ex) {
    System.out.println("never reached for this new error");
}
```

Đây là một **đoạn minh họa**; mặc dù các nhánh là những kiểu hợp lệ, `catch (IllegalStateException)` không nhận lỗi từ `catch` đứng trước. Các `catch` cùng cấp chỉ được chọn cho ngoại lệ thoát ra từ **phần thân `try` tương ứng**, không bảo vệ thân của nhau.

Để bắt ngoại lệ mới từ handler bên trong, đặt **một `try-catch` ngoài**:

```java
try {
    try {
        throw new IllegalArgumentException("bad data");
    } catch (IllegalArgumentException ex) {
        throw new IllegalStateException("conversion failed", ex);
    }
} catch (IllegalStateException ex) {
    System.out.println("outer: " + ex.getMessage());
}
```

**Stdout:**

```text
outer: conversion failed
```

Một ranh giới xử lý được xác định bằng **vùng `try` nào đang bao quanh đoạn thực thi**, không chỉ bằng việc các `catch` tình cờ nằm gần nhau trong cùng tệp.

## 8. `finally` lồng nhau và thứ tự dọn dẹp

Khi rời một `try`, khối `finally` tương ứng thường được thực thi. Nếu ngoại lệ lan qua nhiều cấu trúc lồng nhau, quá trình dọn dẹp xảy ra từ phạm vi trong ra phạm vi ngoài:

```java
try {
    try {
        System.out.println("work");
        throw new IllegalStateException("failed");
    } finally {
        System.out.println("cleanup-inner");
    }
} catch (IllegalStateException ex) {
    System.out.println("handle-outer");
} finally {
    System.out.println("cleanup-outer");
}
```

**Stdout:**

```text
work
cleanup-inner
handle-outer
cleanup-outer
```

Ở đây không có handler trong; `finally` trong chạy trước, ngoại lệ được chuyển cho handler ngoài, sau đó `finally` ngoài chạy.

### `finally` không đảm bảo “không còn lỗi”

`finally` không tự bắt ngoại lệ: nếu không có handler phù hợp, lỗi vẫn lan ra ngoài. Ngoài ra, một `finally` **có thể ném lỗi khác**, làm che khuất lỗi đang chờ xử lý.

Ví dụ:

```java
try {
    try {
        throw new IllegalArgumentException("original");
    } finally {
        throw new IllegalStateException("cleanup");
    }
} catch (IllegalStateException ex) {
    System.out.println(ex.getMessage());
}
```

**Stdout:**

```text
cleanup
```

Ở đây lỗi `IllegalStateException("cleanup")` từ `finally` thay thế đường lan truyền của ngoại lệ ban đầu. Không có nghĩa lỗi `original` chưa từng xảy ra. Vì vậy không nên chủ động `return` hay `throw` từ `finally` nếu không thực sự muốn thay đổi đường đi của lỗi.

Với tài nguyên `AutoCloseable`, `try-with-resources` phù hợp hơn một chuỗi `finally` tự gọi `close()`: khi thân `try` và việc đóng đều thất bại, Java thường giữ lỗi của thân `try` là lỗi chính và đưa lỗi lúc đóng vào danh sách **suppressed exceptions** thay vì mất luôn lỗi chính.

## 9. `try-with-resources` có thể lồng trong `try-catch` ngoài

Xét phương thức sau, **chỉ minh họa**, cần đặt trong một lớp Java và có `Path` hợp lệ khi gọi:

```java
static String readFirstLine(java.nio.file.Path path)
        throws java.io.IOException {
    try (var reader =
                 java.nio.file.Files.newBufferedReader(path)) {
        return reader.readLine();
    }
}
```

Người gọi có thể đặt một ranh giới `try-catch` ngoài:

```java
try {
    String line = readFirstLine(path);
    System.out.println("first line: " + line);
} catch (java.io.IOException ex) {
    System.out.println("Cannot read input file");
}
```

Đoạn gọi cần biến `path` kiểu `java.nio.file.Path` trong cùng phạm vi. Lợi ích:

1. Tầng đọc chịu trách nhiệm **đóng reader** trong `try-with-resources`.
2. Tầng gọi chịu trách nhiệm **quyết định cách báo lỗi đọc tệp**.
3. Nếu lỗi xảy ra trong quá trình mở/đọc/đóng, ngoại lệ I/O có thể thoát ra cho caller xử lý theo hợp đồng.
4. Không cần để handler ngoài trực tiếp nắm và đóng tài nguyên nội bộ của tầng dưới.

Không nên coi `catch` ngoài là công cụ đảm bảo rollback mọi tác động: nếu đã ghi một phần tệp, cập nhật database hoặc gọi dịch vụ khác, cần chính sách khôi phục/giao dịch riêng.

### Handler bên trong có thể che mất thông tin gì?

Nếu tầng đọc viết:

```java
try (var reader =
             java.nio.file.Files.newBufferedReader(path)) {
    return reader.readLine();
} catch (java.io.IOException ex) {
    return "";
}
```

Đây là **một phần thân phương thức minh họa**, giả định phương thức trả `String`. Nếu chuỗi rỗng là dữ liệu hợp lệ, caller không phân biệt được **đọc thành công chuỗi rỗng** với **đọc thất bại**. Chỉ dùng fallback nếu hợp đồng thực sự chấp nhận và hành vi được tài liệu hóa. Nếu dữ liệu bắt buộc, để `IOException` lan lên thường đúng hơn.

## 10. Ranh giới xử lý theo tầng: thư viện, nghiệp vụ và giao tiếp

Trong một ứng dụng Java, `try-catch` lồng nhau thường xuất hiện tự nhiên trên **chuỗi lời gọi giữa các tầng**, chứ không nhất thiết tất cả nằm trong một phương thức.

Ví dụ đường gọi:

```text
controller.handleRequest()
  └─ service.placeOrder()
       └─ repository.save()
            └─ thao tác I/O ném IOException
```

Một cách phân chia trách nhiệm hợp lý:

- **Repository:** biết loại thao tác I/O thất bại, quản lý tài nguyên và có thể ném `IOException` hoặc một ngoại lệ phù hợp với hợp đồng lưu trữ.
- **Service:** biết ngữ cảnh nghiệp vụ. Chỉ bắt lỗi nếu có thể thử lại theo chính sách, thực hiện thao tác bù, hoặc chuyển lỗi sang khái niệm có ý nghĩa ở tầng nghiệp vụ. Khi đổi loại ngoại lệ, cần giữ `cause`.
- **Controller hoặc ranh giới tác vụ:** biết người gọi cần phản hồi gì. Nó có thể chuyển ngoại lệ sang trạng thái thất bại của yêu cầu, mã HTTP thích hợp, hoặc bản ghi lỗi tập trung.

**Không có quy tắc “mỗi tầng phải có đúng một `catch`”.** Nếu service không thể xử lý, để lỗi đi lên là hợp lý. Nếu controller cũng không xử lý được thì cần một ranh giới lỗi chung của framework/ứng dụng. Bắt ở mọi lớp rồi in cùng một stack trace chỉ làm log lặp và mất tính rõ ràng của trách nhiệm.

### Ví dụ Java 17+ chạy được: ranh giới tầng trong và tầng ngoài

Tệp **`ExceptionBoundaryDemo.java`**:

```java
import java.io.IOException;

public class ExceptionBoundaryDemo {

    static void repository(int id) throws IOException {
        if (id == 0) {
            throw new IOException("storage not available");
        }
        System.out.println("repository: saved " + id);
    }

    static void service(int id) throws IOException {
        try {
            repository(id);
        } catch (IOException cause) {
            throw new IOException(
                    "service: cannot save order " + id, cause);
        }
    }

    static void controller(int id) {
        System.out.println("request=" + id);

        try {
            service(id);
            System.out.println("response=OK");
        } catch (IOException ex) {
            System.out.println("response=FAILED");
            System.out.println("message=" + ex.getMessage());
            System.out.println(
                    "cause=" + ex.getCause().getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        controller(7);
        controller(0);
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 ExceptionBoundaryDemo.java
java ExceptionBoundaryDemo
```

**Stdout chính xác:**

```text
request=7
repository: saved 7
response=OK
request=0
response=FAILED
message=service: cannot save order 0
cause=IOException
```

Giải thích:

- `controller(7)` gọi service, repository hoàn tất bình thường; `catch` không được chạy.
- `controller(0)` gọi repository, gặp lỗi `IOException`. Handler trong service bắt lỗi đó, thêm ngữ cảnh order và ném **`IOException` mới chứa cause**.
- Handler ngoài controller nhận lỗi được service bọc, in thông điệp cấp service và kiểu nguyên nhân gốc.
- Vì controller đã bắt và không ném lại, nó chủ động trả trạng thái thất bại minh họa thay vì để exception chưa được xử lý.
- Đây là **mô hình phân tầng**, không chứng minh rằng mọi trường hợp phải bọc `IOException` bằng một `IOException` khác. Nếu thông tin ở service không hữu ích, để lỗi đi lên có thể dễ bảo trì hơn.

Trong hệ thống thật, thông điệp cho người dùng cuối và chi tiết log nội bộ nên tách riêng; không mặc định trả nguyên văn `getMessage()` chứa đường dẫn, cấu hình hay dữ liệu nhạy cảm.

## 11. Phạm vi của biến và tác động đến cách chia nested `try-catch`

Dấu ngoặc của mỗi `try`, `catch`, `finally` tạo **phạm vi khối (block scope)**. Ví dụ sau **cố ý không biên dịch**:

```java
try {
    int value = Integer.parseInt("12");
} catch (NumberFormatException ex) {
    System.out.println(value); // value không nằm trong scope.
}
```

Nếu cả đường thành công và đường xử lý lỗi cần truy cập một giá trị, nên thiết kế biến hoặc kiểu kết quả ở phạm vi thích hợp:

```java
int value;
try {
    value = Integer.parseInt(input);
} catch (NumberFormatException ex) {
    value = defaultValue;
}
System.out.println(value);
```

Đây là **mã minh họa**, giả định `input` và `defaultValue` đã được định nghĩa. Việc gán fallback ở `catch` làm biến được gán trên cả hai đường chạy và Java có thể kiểm tra `definite assignment`; nhưng fallback chỉ hợp lý khi là một phần của hợp đồng, không phải cách giấu lỗi.

Cũng chú ý biến `ex` chỉ có trong phạm vi handler khai báo nó. Đặt `try-catch` quá sâu đôi khi làm thông tin cần thiết không còn ở đúng phạm vi để báo lỗi; điều chỉnh thiết kế dữ liệu hoặc đưa xử lý lên tầng có ngữ cảnh thay vì dùng biến toàn cục.

## 12. Khi nào nên lồng, khi nào nên tách thành phương thức?

Lồng nhiều cấp `try` liên tục không tự làm code an toàn hơn. Một ranh giới có ích khi tầng đó có **mục tiêu xử lý khác biệt**.

**Nên lồng khi:**

- Một thao tác con có fallback độc lập, còn thao tác bao ngoài cần bảo vệ tính toàn vẹn của quy trình lớn hơn.
- Tầng trong cần đổi ngoại lệ kỹ thuật thành một lỗi cấp nghiệp vụ, tầng ngoài quyết định phản hồi.
- Tầng trong quản lý tài nguyên, tầng ngoài quản lý kết quả yêu cầu.
- Bạn cần phân biệt “lỗi đã phục hồi cục bộ” và “lỗi buộc hủy cả tác vụ”.

**Nên cân nhắc tách thay vì lồng sâu khi:**

- Có nhiều cấp `try-catch` cùng bắt một loại và chỉ ghi log rồi ném lại.
- Một phương thức chứa các handler trộn quá nhiều trách nhiệm nhập liệu, truy cập dữ liệu, mạng và giao diện.
- Không rõ đoạn nào được bảo vệ, hoặc không thể kiểm thử riêng từng chính sách phục hồi.
- Quá nhiều `finally` tự quản lý tài nguyên gây nguy cơ lỗi dọn dẹp che lỗi chính.

Một cấu trúc dễ đọc thường là: **phương thức nhỏ quản lý lỗi cục bộ**, **tầng gọi quyết định lỗi của thao tác lớn**. Ngoại lệ vẫn truyền qua chuỗi lời gọi khi không gặp handler.

## 13. Lưu ý với checked exception, multi-catch và lỗi unchecked

### Checked exception ở tầng trong

Nếu lời gọi trong `try` có thể ném checked `IOException`, phạm vi phù hợp phải bắt hoặc khai báo `throws` cho trường hợp chưa được xử lý:

```java
static void caller() throws java.io.IOException {
    try {
        read();
    } catch (java.io.FileNotFoundException ex) {
        System.out.println("missing file");
    }
}
```

Đây là **khung minh họa** nếu `read()` được định nghĩa với `throws IOException`. Bắt `FileNotFoundException` chỉ bao phủ một lớp con; các `IOException` khác có thể đi ra, vì vậy `caller` vẫn khai báo `throws IOException`.

Một cấu trúc `try` ngoài có `catch (IOException)` cũng có thể xử lý phần còn lại, miễn đường thực thi thật sự nằm trong vùng đó.

### Multi-catch trong nested `try`

`catch (IOException | NumberFormatException ex)` là cú pháp hợp lệ khi hai loại lỗi không có quan hệ cha/con, và hành động xử lý giống nhau. Nhưng `catch (NumberFormatException | IllegalArgumentException ex)` **không hợp lệ** vì `NumberFormatException extends IllegalArgumentException`.

Multi-catch không thay đổi cách xét phạm vi trong-ra-ngoài; nó chỉ gom nhiều kiểu vào **một handler** của một cấu trúc `try`.

### Lỗi unchecked

`catch (Exception)` ngoài có thể bắt nhiều loại lỗi, bao gồm `RuntimeException`, nhưng không bắt `Error`. Đừng dùng `catch (Throwable)` ở mọi cấp với mục đích “giữ ứng dụng sống”, vì `Error` có thể biểu thị điều kiện mà ứng dụng không thể phục hồi an toàn.

Một ranh giới tổng quát ở đầu yêu cầu hoặc đầu tác vụ có thể cần báo lỗi tập trung, nhưng vẫn phải giữ nguyên nhân, trạng thái thất bại và chính sách xử lý phù hợp.

## 14. Nested `try-catch` và thread: ranh giới không tự vượt qua luồng khác

Ví dụ:

```java
try {
    Thread worker = new Thread(() -> {
        throw new IllegalStateException("worker failed");
    });
    worker.start();
} catch (IllegalStateException ex) {
    System.out.println("caught in parent");
}
```

Đây là **đoạn minh họa**, không nên dùng để chứng minh stdout cố định vì hành vi uncaught handler tùy môi trường. Handler quanh `worker.start()` bảo vệ lời gọi `start()` trong thread gọi, **không tự bắt** ngoại lệ ném sau đó khi `Runnable` chạy trên stack của worker khác.

Tương tự, `worker.join()` chỉ chờ thread kết thúc, không tự ném lại lỗi đã xảy ra trong worker. Nếu cần truyền thành công/thất bại về nơi điều phối, dùng `Future`, `CompletableFuture` hoặc một cơ chế báo cáo đúng thiết kế.

Phạm vi `try-catch` là **phạm vi thực thi động của thread hiện tại**, không phải “mọi công việc bắt nguồn từ câu lệnh đã được gọi”. Đây là ranh giới quan trọng khi chuyển từ Java đồng bộ sang đa luồng.

## 15. Lỗi thiết kế thường gặp

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| Bắt tất cả lỗi ở `try` trong rồi bỏ qua | Tầng ngoài không biết thao tác thất bại | Chỉ phục hồi khi có hợp đồng rõ; nếu không, lan truyền hoặc chuyển lỗi |
| Mong `catch` ngoài cụ thể hơn được ưu tiên trước `catch` trong kiểu cha | Hiểu sai thứ tự tìm handler | Xét vùng đang hoạt động từ trong ra ngoài trước |
| Nghĩ xử lý ở `catch` ngoài sẽ chạy tiếp phần còn lại của `try` ngoài | Bỏ sót đường thực thi bị gián đoạn | Đặt logic tiếp tục sau cấu trúc hoặc chủ động retry |
| Mong `catch` anh em bắt ngoại lệ ném từ một `catch` khác | Ngoại lệ mới thoát ra ngoài | Dùng `try-catch` ngoài hoặc tổ chức lại handler |
| Dùng `finally` ném lỗi mới không chủ đích | Có thể che nguyên nhân ban đầu | Dọn dẹp an toàn; ưu tiên `try-with-resources` |
| Lặp lại log và wrapper ở mọi cấp | Nhiễu log, chuỗi cause dài | Đặt ranh giới log/chuyển lỗi có trách nhiệm |
| Bọc lỗi nhưng không truyền `cause` | Mất thông tin chẩn đoán | Dùng constructor nhận `Throwable cause` |
| Bắt `IOException` trong cùng `try` không hề có nguồn lỗi checked tương thích | Có thể không biên dịch | Bắt quanh lời gọi thực tế có thể ném loại đó |
| Bao toàn bộ phương thức trong `try` quá rộng | Không xác định được nguồn lỗi, dễ trả thông báo sai | Thu hẹp theo thao tác và chính sách xử lý |
| Trông đợi handler bắt lỗi của thread được `start()` | Ngoại lệ ở stack khác | Dùng kênh báo cáo/quan sát lỗi của tác vụ |

## 16. Bài tập tự kiểm tra

**Bài 1 — handler trong có kiểu cha:**

```java
try {
    try {
        throw new NumberFormatException("x");
    } catch (IllegalArgumentException ex) {
        System.out.println("inner");
    }
} catch (NumberFormatException ex) {
    System.out.println("outer");
}
```

**Đáp án:** chỉ in `inner`. `NumberFormatException` thuộc `IllegalArgumentException` và đã được handler trong xử lý.

**Bài 2 — lỗi không khớp trong, có `finally`:**

```java
try {
    try {
        throw new IllegalStateException("broken");
    } catch (NumberFormatException ex) {
        System.out.println("wrong catch");
    } finally {
        System.out.println("cleanup");
    }
} catch (IllegalStateException ex) {
    System.out.println("handled outside");
}
```

**Đáp án:**

```text
cleanup
handled outside
```

Lỗi lan ra khỏi khối trong sau khi `finally` thực thi.

**Bài 3 — lỗi được chuyển trong `catch`:**

```java
try {
    try {
        throw new IllegalArgumentException("bad");
    } catch (IllegalArgumentException ex) {
        throw new IllegalStateException("wrapped", ex);
    }
} catch (IllegalStateException ex) {
    System.out.println(ex.getCause().getMessage());
}
```

**Đáp án:** in `bad`. Lỗi ngoài là `IllegalStateException`, `cause` là ngoại lệ `IllegalArgumentException` ban đầu.

**Bài 4 — câu lệnh sau cấu trúc trong có chạy?**

Nếu `try` trong ném `IllegalStateException` không được handler trong bắt, handler ngoài bắt được và kết thúc bình thường, câu lệnh **nằm trong `try` ngoài, ngay sau cấu trúc trong** có chạy hay không?

**Không.** Cấu trúc trong hoàn tất bất thường, khiến phần `try` ngoài bị gián đoạn; sau handler ngoài, luồng tiếp tục sau cấu trúc `try-catch` ngoài (nếu nó hoàn tất bình thường).

**Bài 5 — lỗi trong `finally`:**

Nếu phần `try` trong đang ném ngoại lệ A, nhưng `finally` trong lại ném ngoại lệ B, handler ngoài sẽ nhận ngoại lệ nào theo quy tắc hoàn tất của Java?

**Thông thường là B.** Đường hoàn tất bất thường do `finally` mới có thể thay thế A. Với tài nguyên `AutoCloseable`, `try-with-resources` có cơ chế suppressed exceptions giúp lưu lỗi khi đóng tài nguyên, nên không thể suy luận mọi lỗi trong `finally` đều được giữ tự động.

**Bài 6 — lỗi của thread khác:**

Có thể bao `new Thread(task).start()` trong `try-catch` để bắt `RuntimeException` mà chính `task.run()` ném sau đó không?

**Không tự động.** `task.run()` chạy trong thread mới, không nằm trên ngăn xếp lời gọi của thread thực hiện `start()`.

## 17. Checklist thiết kế ranh giới xử lý

Khi chuẩn bị thêm một tầng `try-catch`, hãy tự hỏi:

1. **Tầng này sở hữu thao tác nào?** Xác định câu lệnh và chuỗi lời gọi cần bảo vệ; tránh bắt nhầm từ phần mã không liên quan.
2. **Tầng trong có sửa được lỗi không?** Nếu có chính sách phục hồi được kiểm thử, xử lý tại đây. Nếu không, đừng bắt chỉ để bỏ qua.
3. **Lỗi còn lại đi đâu?** Nếu không có handler trong khớp, nó sẽ đi ra phạm vi ngoài sau dọn dẹp.
4. **Có chuyển lỗi sang loại mới không?** Nếu có, kiểu handler ngoài phải tương thích với **đối tượng mới**, và `cause` nên giữ lỗi gốc.
5. **Sau khi handler chạy thì chương trình tiếp tục ở đâu?** Không giả định các câu lệnh đã bị bỏ qua trong `try` sẽ chạy bù.
6. **`finally` và tài nguyên có an toàn không?** Giảm việc ném lỗi/`return` trong `finally`; ưu tiên `try-with-resources` khi phù hợp.
7. **Checked exception đã được khai báo hoặc bắt đủ chưa?** Một handler lớp con không luôn bao phủ toàn bộ loại cha được khai báo bởi lời gọi.
8. **Có đổi thread không?** Nếu có, đặt cơ chế nhận kết quả/lỗi ở ranh giới đồng thời phù hợp.
9. **Có bị lặp logging và wrapper không?** Chỉ thêm tầng xử lý nếu tầng đó có hành động hoặc ngữ cảnh mới.
10. **Đã kiểm thử từng nhánh chưa?** Ít nhất cần đường thành công, lỗi bắt trong, lỗi lan ngoài, lỗi được wrap và trường hợp dọn dẹp.

**Kết luận:** `try-catch` lồng nhau không phải “nhiều lớp bảo hiểm tự động”, mà là cách diễn đạt **ranh giới trách nhiệm**. Handler trong được xét trước đối với lỗi thuộc phạm vi của nó; nếu nó không khớp hoặc ném lỗi mới, tầng ngoài có thể xử lý. Thiết kế tốt giúp lỗi có đường đi rõ ràng, tài nguyên được quản lý ở đúng nơi và caller nhận được thất bại thật thay vì trạng thái thành công giả.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="try-catch-009">Catch không thể tới do khối catch trước đã bao phủ</a>

<details>
<summary>Xem chi tiết</summary>

# Catch không thể tới do khối catch trước đã bao phủ

## 1. Mục tiêu học tập

Trong Java, các mệnh đề `catch` của **cùng một câu lệnh `try`** được xét theo thứ tự từ trên xuống khi xảy ra ngoại lệ. Nếu một `catch` đứng trước đã bắt được **mọi đối tượng thuộc kiểu mà `catch` đứng sau định bắt**, `catch` đứng sau không có cơ hội nhận ngoại lệ. Java phát hiện trường hợp này khi biên dịch và báo lỗi.

Sau bài học, bạn có thể:

- Giải thích vì sao thứ tự `catch` liên quan trực tiếp đến quan hệ kế thừa giữa các lớp ngoại lệ.
- Nhận ra lỗi một `catch` không thể tới được vì `catch` trước đã bao phủ kiểu của nó.
- Sắp xếp các handler từ kiểu cụ thể đến kiểu tổng quát mà vẫn giữ đúng ý nghĩa xử lý.
- Phân biệt **lỗi thứ tự do bị bao phủ** với **lỗi do bắt checked exception không thể được ném từ khối `try`**.
- Viết đúng `multi-catch` và nhận ra trường hợp các nhánh của multi-catch bị trùng quan hệ cha–con.
- Hiểu tác động của `catch (Exception)`, `catch (Throwable)`, các nhánh anh em, `try` lồng nhau và `finally`.
- Đọc lỗi của `javac`, sửa đúng vị trí và kiểm tra lại bằng chương trình Java 17+.

## 2. Cơ chế lựa chọn `catch` khi một ngoại lệ xuất hiện

Một `try` có thể có nhiều `catch` đứng liên tiếp:

```java
try {
    riskyOperation();
} catch (SpecificException e) {
    handleSpecific(e);
} catch (GeneralException e) {
    handleGeneral(e);
}
```

Đây là đoạn mã minh họa, cần định nghĩa các kiểu ngoại lệ và phương thức tương ứng. Về nguyên tắc, Java xem các `catch` theo **thứ tự viết trong mã nguồn**, chọn handler đầu tiên có kiểu tương thích với **đối tượng ngoại lệ thực tế**. Các `catch` còn lại của cùng `try` không chạy cho đối tượng đó.

Điều kiện tương thích có thể hiểu theo phép gán kiểu tham chiếu: một handler kiểu `Exception` có thể nhận đối tượng `IOException` vì `IOException` kế thừa `Exception`; handler kiểu `IOException` không nhận một đối tượng `Exception` bất kỳ.

Ví dụ sơ đồ rút gọn:

```text
java.lang.Throwable
├── java.lang.Error
│   ├── AssertionError
│   └── ...
└── java.lang.Exception
    ├── java.io.IOException
    │   ├── java.io.FileNotFoundException
    │   └── ...
    ├── java.sql.SQLException
    └── java.lang.RuntimeException
        ├── IllegalArgumentException
        │   └── NumberFormatException
        ├── NullPointerException
        └── ...
```

Đây là các nhánh tiêu biểu, không phải toàn bộ cây `Throwable`.

Hai quy tắc cần ghi nhớ:

1. **Lớp cha đứng trước lớp con trong các `catch` cùng cấp** là một trường hợp bị bao phủ: `catch (Exception)` đứng trước `catch (IOException)` khiến nhánh sau không thể được chọn.
2. **Lớp con đứng trước lớp cha** có thể hợp lệ: `catch (FileNotFoundException)` rồi `catch (IOException)` cho phép xử lý lỗi không tìm thấy file riêng, còn các lỗi I/O khác do handler rộng hơn nhận.

`catch` không hoạt động như câu lệnh `if` độc lập mà mọi nhánh có thể lần lượt chạy. Chỉ một handler của **cùng `try`** được chọn cho ngoại lệ đang lan truyền; sau khi handler đó hoàn tất, không có cơ chế “fall-through” sang handler tiếp theo.

## 3. Vì sao Java từ chối một `catch` đã bị bao phủ?

Xét ví dụ sau. **Đây là mã cố ý không biên dịch**; lưu thành `WrongCatchOrder.java` để quan sát lỗi:

```java
import java.io.IOException;

public class WrongCatchOrder {
    public static void main(String[] args) {
        try {
            throw new IOException("read failed");
        } catch (Exception failure) {
            System.out.println("general handler");
        } catch (IOException failure) {
            System.out.println("IO handler");
        }
    }
}
```

Lệnh kiểm tra:

```bash
javac WrongCatchOrder.java
```

`javac` báo lỗi biên dịch tại handler `catch (IOException failure)`. Thông điệp tiếng Anh trên JDK phổ biến có ý tương đương **“exception IOException has already been caught”**; vị trí và định dạng chi tiết có thể khác theo phiên bản/compiler. Không có lệnh `java WrongCatchOrder` hợp lệ vì quá trình biên dịch chưa tạo chương trình thành công.

Nguyên nhân:

- Kiểu thực tế `IOException` có thể gán cho `Exception`.
- Handler `catch (Exception)` đứng trước và đã bao phủ toàn bộ các đối tượng `IOException`.
- Do đó handler `catch (IOException)` ở dưới không bao giờ được chọn trong cùng `try`.
- Trình biên dịch phát hiện xung đột này ngay cả khi không chạy chương trình; đây là **lỗi ngôn ngữ tại compile time**.

Việc đặt `throw new IOException(...)` trong khối `try` chỉ giúp ví dụ có tình huống rõ ràng. Ngay cả khi luồng thực thi thực tế chưa từng phát sinh lỗi, **mối quan hệ kiểu và thứ tự handler** vẫn khiến hai handler ở trên không hợp lệ.

### Cách sửa

Đặt handler cụ thể trước, handler rộng sau:

```java
import java.io.IOException;

public class CorrectCatchOrder {
    public static void main(String[] args) {
        try {
            throw new IOException("read failed");
        } catch (IOException failure) {
            System.out.println("IO handler: " + failure.getMessage());
        } catch (Exception failure) {
            System.out.println("general handler");
        }
    }
}
```

Lưu thành **`CorrectCatchOrder.java`**, biên dịch và chạy:

```bash
javac CorrectCatchOrder.java
java CorrectCatchOrder
```

Kết quả `stdout` chính xác:

```text
IO handler: read failed
```

`catch (IOException)` nhận đối tượng và in thông tin lỗi. `catch (Exception)` ở cuối không chạy đối với lần ném này. Handler rộng có thể phục vụ những ngoại lệ thuộc nhánh `Exception` khác mà đoạn `try` có thể ném. Trong ví dụ đơn giản, handler rộng không bắt buộc để xử lý riêng `IOException`, nhưng cấu trúc **cụ thể trước, tổng quát sau** là hợp lệ.

## 4. Cơ chế bao phủ dựa trên kiểu, không dựa vào tên biến

Đặt cạnh nhau hai đoạn mã:

**Sai, lớp cha đứng trước lớp con:**

```java
try {
    load();
} catch (java.io.IOException ex) {
    // Bao phủ mọi FileNotFoundException.
} catch (java.io.FileNotFoundException missing) {
    // Không thể tới: FileNotFoundException là IOException.
}
```

Đây là **mảnh minh họa lỗi biên dịch**, giả định `load()` là một lời gọi có thể phát sinh checked `IOException`. Việc đổi tên biến `missing` hoặc `ex` không thay đổi quan hệ kế thừa giữa các kiểu.

**Đúng về thứ tự:**

```java
try {
    load();
} catch (java.io.FileNotFoundException missing) {
    // Xử lý riêng trường hợp file không tồn tại.
} catch (java.io.IOException ex) {
    // Xử lý những IOException khác.
}
```

Đây là **mảnh minh họa**, cần có `load()` được định nghĩa phù hợp. Handler kiểu cha được đặt sau có thể bắt những kiểu cùng nhánh không bị handler trước bao phủ.

Hình dung tập kiểu bắt được:

```text
catch (IOException)
    ├── IOException
    ├── FileNotFoundException
    └── các lớp con IOException khác

catch (FileNotFoundException)
    └── FileNotFoundException và lớp con của nó
```

Nếu tập kiểu của `catch` sau **nằm hoàn toàn trong** tập kiểu của `catch` trước, handler sau bị bao phủ. Nếu hai tập kiểu là hai nhánh không bao phủ nhau, ta không kết luận lỗi thứ tự chỉ vì handler đặt trước hay sau.

## 5. Ví dụ chạy được: ba lớp xử lý theo mức độ cụ thể

Tạo file **`CatchHierarchyDemo.java`**:

```java
import java.io.FileNotFoundException;
import java.io.IOException;

public class CatchHierarchyDemo {
    private static void load(String mode) throws IOException {
        if ("missing".equals(mode)) {
            throw new FileNotFoundException("missing.txt");
        }
        if ("io".equals(mode)) {
            throw new IOException("connection interrupted");
        }
        if ("bad".equals(mode)) {
            throw new IllegalArgumentException("invalid mode");
        }
    }

    private static void run(String mode) {
        try {
            load(mode);
            System.out.println(mode + ": success");
        } catch (FileNotFoundException failure) {
            System.out.println(mode + ": file not found");
        } catch (IOException failure) {
            System.out.println(mode + ": IO failure");
        } catch (IllegalArgumentException failure) {
            System.out.println(mode + ": invalid argument");
        }
    }

    public static void main(String[] args) {
        run("missing");
        run("io");
        run("bad");
        run("ok");
    }
}
```

```bash
javac CatchHierarchyDemo.java
java CatchHierarchyDemo
```

Kết quả `stdout`:

```text
missing: file not found
io: IO failure
bad: invalid argument
ok: success
```

Giải thích:

- `load("missing")` tạo `FileNotFoundException`; handler đầu tiên nhận lỗi vì đây là kiểu cụ thể nhất.
- `load("io")` tạo `IOException` không phải `FileNotFoundException`; handler thứ hai nhận.
- `load("bad")` tạo `IllegalArgumentException`, thuộc nhánh `RuntimeException`; hai handler I/O không tương thích và handler thứ ba nhận.
- `load("ok")` hoàn tất bình thường và chạy dòng thông báo thành công.

Trong ví dụ, `IOException` và `IllegalArgumentException` không có quan hệ kế thừa kiểu cha–con, nên việc đặt hai handler ở các vị trí khác nhau không tự tạo vấn đề bao phủ. Vẫn nên đặt chúng theo thứ tự dễ đọc và dựa trên ý nghĩa xử lý của ứng dụng.

## 6. `catch (Exception)` và `catch (Throwable)` có thể che khuất nhánh nào?

Hai kiểu này rất rộng nhưng không hoàn toàn tương đương.

### 6.1. `catch (Exception)` trước mọi lớp con `Exception`

Ví dụ **không biên dịch**:

```java
try {
    int value = Integer.parseInt("abc");
    System.out.println(value);
} catch (Exception failure) {
    System.out.println("general");
} catch (NumberFormatException failure) {
    System.out.println("parse failed");
}
```

`NumberFormatException` kế thừa `IllegalArgumentException → RuntimeException → Exception`. Vì `Exception` đứng trước, handler `NumberFormatException` bị bao phủ. Đổi thứ tự nếu thực sự cần xử lý riêng lỗi parse.

### 6.2. `catch (Throwable)` còn rộng hơn `catch (Exception)`

`Throwable` là kiểu cha của cả `Exception` và `Error`. Vì vậy:

```java
try {
    operation();
} catch (Throwable failure) {
    // Bao phủ cả Exception lẫn Error.
} catch (Exception failure) {
    // Không thể tới: đã thuộc Throwable.
}
```

Đây là **mảnh minh họa lỗi biên dịch**, cần định nghĩa `operation()` trong chương trình thật. Cùng lý do, đặt `catch (Throwable)` trước `catch (Error)` cũng làm nhánh `Error` phía sau không thể được chọn.

Trong ứng dụng thông thường, `catch (Throwable)` không nên dùng chỉ để “bắt tất cả”, vì nó còn bao gồm nhiều `Error` nghiêm trọng mà ứng dụng không chắc có thể phục hồi.

### 6.3. `catch (Exception)` trước `catch (Error)` thì sao?

Hai nhánh `Exception` và `Error` **không nằm trong quan hệ cha–con**. Vì vậy, riêng xét vấn đề bao phủ kiểu, cách sắp xếp sau hợp lệ:

```java
try {
    operation();
} catch (Exception failure) {
    handleException(failure);
} catch (Error failure) {
    handleError(failure);
}
```

Đây là **mảnh minh họa**, không phải khuyến nghị luôn bắt `Error`. Các phương thức phải được định nghĩa. `catch (Exception)` không bắt đối tượng thuộc nhánh `Error`. Tuy nhiên, một số `Error` như `OutOfMemoryError` và `StackOverflowError` có thể khiến trạng thái hoặc tài nguyên JVM không còn đủ an toàn để tiếp tục, nên cần chính sách vận hành phù hợp.

## 7. `multi-catch` và trường hợp bị bao phủ

Từ Java 7, bạn có thể viết nhiều lựa chọn ngoại lệ trong **một `catch`** bằng dấu `|`:

```java
catch (IOException | SQLException failure) {
    // Xử lý chung hai nhánh ngoại lệ khác nhau.
}
```

`IOException` và `SQLException` là hai lớp checked exception khác nhánh; chúng không kế thừa nhau, vì vậy có thể cùng xuất hiện trong một multi-catch. Biến `failure` của multi-catch có tính chất hạn chế gán lại; bạn không thể gán đối tượng mới cho nó.

### 7.1. Multi-catch không cho phép các lựa chọn có quan hệ cha–con

Ví dụ **không biên dịch**:

```java
try {
    load();
} catch (java.io.FileNotFoundException
         | java.io.IOException failure) {
    System.out.println("IO failed");
}
```

`FileNotFoundException` đã thuộc `IOException` nên hai lựa chọn trong **cùng một multi-catch** có quan hệ kế thừa; Java từ chối cấu trúc này. Đây là quy tắc riêng của `multi-catch`, tương tự mục tiêu tránh những lựa chọn dư thừa do kiểu bao phủ.

Viết thành một handler rộng:

```java
try {
    load();
} catch (java.io.IOException failure) {
    System.out.println("IO failed");
}
```

Hoặc tách các handler cụ thể/rộng theo thứ tự đúng nếu cách xử lý khác nhau:

```java
try {
    load();
} catch (java.io.FileNotFoundException missing) {
    System.out.println("Missing file");
} catch (java.io.IOException failure) {
    System.out.println("Other IO issue");
}
```

Các đoạn trong mục này đều là **mảnh minh họa**, cần có `load()` có hợp đồng throws phù hợp và đặt trong phương thức/lớp hợp lệ.

### 7.2. Một `multi-catch` đứng trước có thể che khuất `catch` đứng sau

Ví dụ **không biên dịch**:

```java
try {
    process();
} catch (java.io.IOException | java.sql.SQLException failure) {
    handle(failure);
} catch (java.io.FileNotFoundException missing) {
    handle(missing);
}
```

Nhánh `FileNotFoundException` bị bao phủ bởi lựa chọn `IOException` trong multi-catch đứng trước. Mặc dù `SQLException` không liên quan đến `FileNotFoundException`, chỉ cần **một** lựa chọn ở trước đã bao phủ toàn bộ kiểu của handler sau là đủ để tạo lỗi.

Đoạn này minh họa quy tắc thứ tự; `process()` và `handle(...)` cần được khai báo tương thích.

### 7.3. Ví dụ multi-catch hợp lệ, chạy được

Tạo file **`MultiCatchDemo.java`**:

```java
import java.io.IOException;
import java.sql.SQLException;

public class MultiCatchDemo {
    private static void execute(String mode)
            throws IOException, SQLException {
        if ("io".equals(mode)) {
            throw new IOException("read error");
        }
        if ("sql".equals(mode)) {
            throw new SQLException("query error");
        }
    }

    private static void run(String mode) {
        try {
            execute(mode);
            System.out.println(mode + ": done");
        } catch (IOException | SQLException failure) {
            System.out.println(mode + ": "
                    + failure.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        run("io");
        run("sql");
        run("ok");
    }
}
```

```bash
javac MultiCatchDemo.java
java MultiCatchDemo
```

Kết quả `stdout`:

```text
io: IOException
sql: SQLException
ok: done
```

`throws IOException, SQLException` sử dụng **dấu phẩy** để khai báo nhiều kiểu trên phương thức; `catch (IOException | SQLException failure)` sử dụng **dấu `|`** để xử lý hai kiểu khác nhánh trong một handler. Hai cú pháp này có chức năng và quy tắc khác nhau.

## 8. Hai lỗi biên dịch dễ bị nhầm lẫn

Không phải mọi thông báo “unreachable” hoặc “never thrown” liên quan đến `catch` đều có cùng nguyên nhân.

### 8.1. Bị `catch` đứng trước bao phủ

```java
try {
    throw new java.io.IOException("x");
} catch (Exception failure) {
    // Bắt cả IOException.
} catch (java.io.IOException failure) {
    // Không thể tới: handler trước bao phủ.
}
```

Đoạn **không biên dịch** vì thứ tự các kiểu bắt. Dù trong `try` có ném `IOException`, handler cụ thể vẫn bị handler tổng quát chiếm hết.

### 8.2. Bắt checked exception mà khối `try` không thể ném

```java
try {
    int x = 1 + 2;
    System.out.println(x);
} catch (java.io.IOException failure) {
    System.out.println("IO error");
}
```

Đoạn **không biên dịch** vì trong `try` không có thao tác nào có thể ném checked `IOException` theo phân tích của trình biên dịch. Không có `catch` trước để che khuất; lỗi nằm ở **tính khả đạt và kiểm tra ngoại lệ từ khối `try`**.

Hai trường hợp cần sửa khác nhau:

| Lỗi | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| `IOException` sau `Exception` | Kiểu bị handler đứng trước bao phủ | Đưa `IOException` lên trên, hoặc bỏ handler dư |
| `catch (IOException)` trong `try` chỉ làm phép cộng và in | Không có đường ném checked `IOException` phù hợp | Bỏ handler không cần thiết, hoặc chuyển nó đến khối `try` thực sự gọi API I/O |
| `IOException` sau `FileNotFoundException` với `try` có thể ném `IOException` | Handler rộng vẫn có khả năng nhận loại I/O khác | Có thể hợp lệ, giữ thứ tự cụ thể trước |
| `catch (RuntimeException)` để nhận lỗi unchecked có thể xảy ra | Quy tắc kiểm tra khác checked exception | Không tự kết luận lỗi chỉ vì đường thử chưa từng ném |

Đặc biệt, một `catch` rộng đứng sau vài `catch` cụ thể có thể hoàn toàn hợp lệ, miễn là nó còn có thể nhận một loại ngoại lệ khác theo quy tắc của Java. Không nên chỉ dựa vào số lần handler đã chạy trong các bài kiểm thử để suy ra mã đúng hoặc sai khi biên dịch.

### 8.3. Có được bắt một kiểu con sau kiểu cha nếu cố tình viết `if`?

Không. Java kiểm tra quan hệ kiểu của chính các handler đứng cùng cấp. Bạn không thể sửa lỗi bằng cách thêm điều kiện trong `catch` đứng trước:

```java
try {
    load();
} catch (java.io.IOException failure) {
    if (!(failure instanceof java.io.FileNotFoundException)) {
        System.out.println("Other IO error");
    }
} catch (java.io.FileNotFoundException missing) {
    // Vẫn không thể tới.
}
```

Đây là **mảnh cố ý không biên dịch**. Dù handler trước chỉ thực hiện một nhánh `if` với một số ngoại lệ, handler ấy vẫn **bắt** mọi đối tượng `IOException`. Nếu muốn xử lý riêng `FileNotFoundException`, hãy đặt handler cụ thể lên trước; nếu muốn phân nhánh trong một handler rộng, làm ngay **bên trong cùng handler** và không tạo `catch` con ở phía sau.

## 9. Khác biệt giữa các `catch` cùng cấp và `try` lồng nhau

Quy tắc che khuất được xét đối với chuỗi `catch` của **cùng một `try`**. Một `try` nằm bên trong một `catch` hoặc một `try` khác tạo phạm vi xử lý riêng.

Ví dụ chạy được **`NestedCatchDemo.java`**:

```java
import java.io.FileNotFoundException;
import java.io.IOException;

public class NestedCatchDemo {
    private static void read() throws IOException {
        throw new FileNotFoundException("config.txt");
    }

    public static void main(String[] args) {
        try {
            try {
                read();
            } catch (FileNotFoundException missing) {
                System.out.println("inner: missing file");
                throw new IOException("cannot recover", missing);
            }
        } catch (IOException failure) {
            System.out.println("outer: " + failure.getMessage());
        }

        System.out.println("done");
    }
}
```

```bash
javac NestedCatchDemo.java
java NestedCatchDemo
```

Kết quả `stdout`:

```text
inner: missing file
outer: cannot recover
done
```

Lúc đầu, handler **bên trong** nhận `FileNotFoundException`. Handler đó ném **một đối tượng `IOException` mới** để báo rằng không thể phục hồi; `try` **bên ngoài** bắt đối tượng mới này. Hai handler không đứng cùng chuỗi `catch` nên không có lỗi “handler trước bao phủ handler sau”.

Cần nhớ: một ngoại lệ **được ném từ chính thân `catch`** không được các `catch` anh em của cùng `try` bắt lại. Nó đi ra phạm vi bao quanh tương ứng, và có thể được một `try` bên ngoài xử lý. Điều này khác với việc nhiều `catch` trong cùng danh sách chạy nối tiếp.

### `finally` không phải một handler thay thế

Nếu có `finally`, nó tham gia khi rời khối theo cơ chế bình thường hoặc do ngoại lệ. Nó không giúp một `catch` đã bị kiểu trước bao phủ trở nên hợp lệ:

```java
try {
    read();
} catch (java.io.IOException failure) {
    System.out.println("read failed");
} finally {
    System.out.println("cleanup");
}
```

Đây là **mảnh mã minh họa** với `read()` cần khai báo thích hợp. Thứ tự `catch` vẫn phải hợp lệ; `finally` không thay đổi quy tắc kiểm tra handler tại compile time. Nếu `finally` tự ném lỗi, nó có thể thay đổi ngoại lệ cuối cùng lan truyền, nhưng đó là một vấn đề luồng thực thi khác.

## 10. Khi các ngoại lệ là anh em, thứ tự có còn quan trọng?

Ví dụ `IOException` và `SQLException` đều kế thừa `Exception` nhưng không kế thừa nhau. Vì vậy, hai handler này không che khuất nhau về kiểu:

```java
try {
    process();
} catch (java.io.IOException io) {
    handleIo(io);
} catch (java.sql.SQLException sql) {
    handleSql(sql);
}
```

Với các phương thức `process()`, `handleIo()` và `handleSql()` được định nghĩa hợp lệ, đổi vị trí hai handler có thể vẫn biên dịch. Tuy nhiên, **điều kiện checked exception từ `try`** phải được thỏa như đã trình bày ở mục 8.

Tương tự, `NullPointerException` và `IllegalArgumentException` là hai nhánh khác nhau dưới `RuntimeException`. Một handler `RuntimeException` đặt phía trên sẽ bao phủ cả hai; nhưng hai handler chuyên biệt đặt cạnh nhau không che khuất nhau.

Đừng nhầm quan hệ cùng xuất phát từ một tổ tiên với quan hệ **kiểu này là con của kiểu kia**. Chỉ có quan hệ bao phủ thực sự mới khiến một handler sau không còn khả năng nhận đối tượng thuộc kiểu của nó.

## 11. Nguyên tắc thiết kế handler dễ bảo trì

### 11.1. Bắt kiểu đủ cụ thể ở vị trí có thể xử lý

Nếu `FileNotFoundException` yêu cầu báo người dùng chọn lại đường dẫn, nhưng `IOException` khác cần retry có giới hạn, bạn có cơ sở để tách handler. Nếu hai tình huống dùng đúng cùng cách xử lý, một handler `IOException` có thể là đủ; không cần thêm `FileNotFoundException` chỉ để làm mã dài hơn.

### 11.2. Handler tổng quát thường đặt cuối, nếu thật sự cần

`catch (Exception)` bao gồm cả các `RuntimeException` như `NullPointerException`. Handler quá rộng có thể che giấu lỗi lập trình nếu chỉ ghi log sơ sài rồi tiếp tục báo thành công. Chọn ranh giới phù hợp để xử lý và cân nhắc ném tiếp khi không phục hồi được.

`catch (Throwable)` còn bao gồm cả `Error`. Chỉ dùng trong những hạ tầng đặc biệt có chính sách rõ ràng; đừng lạm dụng để khắc phục lỗi thứ tự `catch`.

### 11.3. Giữ nguyên nguyên nhân khi bọc lỗi

Nếu muốn chuyển đổi `FileNotFoundException` thành một ngoại lệ tầng nghiệp vụ, giữ `cause` qua constructor phù hợp hoặc `initCause`. Việc bọc không làm handler `catch` đã bị bao phủ trở nên chạy được; đó là thay đổi **đường truyền và kiểu ngoại lệ** cần được thiết kế riêng.

### 11.4. Kiểm tra ngay bằng biên dịch

Đối với lỗi handler bị bao phủ, lệnh `javac` đủ để phát hiện. Không cần cố chạy để kiểm tra nhánh bất khả đạt. Hãy sửa thứ tự các kiểu dựa trên hệ phân cấp; sau khi biên dịch thành công mới kiểm tra kết quả theo từng trường hợp đầu vào.

Một quy trình ngắn:

1. Liệt kê các kiểu xuất hiện trong chuỗi `catch`.
2. Vẽ quan hệ `extends` giữa chúng hoặc tra JavaDoc.
3. Nếu một kiểu con đứng sau kiểu cha đã bao phủ nó, di chuyển handler con lên trước hoặc bỏ handler thừa.
4. Với checked exception, bảo đảm khối `try` thực sự có khả năng ném kiểu tương ứng.
5. Với multi-catch, kiểm tra các lựa chọn không có quan hệ cha–con.
6. Biên dịch, sau đó thử ít nhất một đường đi cho mỗi handler có ý nghĩa.

## 12. Những hiểu lầm phổ biến

**“`catch` phía sau sẽ chạy nếu `catch` trước không in gì.”** Sai. Một handler vẫn **bắt** đối tượng dù thân nó trống hoặc không ghi log. `catch` kế tiếp của cùng `try` không được thử lại.

**“`catch (Exception)` đứng trước `catch (IOException)` hợp lệ nếu tôi chỉ xử lý RuntimeException trong handler đầu.”** Sai. Kiểu bắt của handler đầu vẫn là `Exception`, bao gồm cả `IOException`, bất kể phần thân có `if` nào.

**“Chỉ checked exception mới bị lỗi khi đặt handler con sau cha.”** Sai. `RuntimeException` đứng trước `NullPointerException` cũng gây che khuất. Với `Throwable` đứng trước `Error` cũng vậy.

**“`catch (Exception)` bao phủ mọi `Error`.”** Sai. `Error` cùng kế thừa `Throwable` nhưng không kế thừa `Exception`.

**“Hai kiểu cùng có cha `Exception` thì luôn che nhau.”** Sai. Chẳng hạn `IOException` và `SQLException` không kế thừa nhau.

**“`catch (FileNotFoundException | IOException e)` là cách viết gọn.”** Sai. Các lựa chọn trong multi-catch không được có quan hệ cha–con; chỉ dùng `catch (IOException e)` hoặc tách handler theo thứ tự phù hợp.

**“`catch (IOException)` sau `catch (FileNotFoundException)` lúc nào cũng sai.”** Sai. Handler rộng có thể bắt các `IOException` còn lại nếu khối `try` có thể tạo chúng theo phân tích checked exception.

**“Nếu `catch` không bao giờ chạy trong unit test, trình biên dịch bắt buộc báo không thể tới.”** Sai. Không bao giờ xảy ra trong một bộ dữ liệu thử nghiệm khác với tính khả đạt theo quy tắc ngôn ngữ.

**“Chuyển `catch` đến một khối `try` lồng nhau vẫn bị lỗi vì trùng kiểu.”** Sai. Mỗi chuỗi `catch` gắn với câu lệnh `try` của nó; các handler ở phạm vi khác nhau có thể phục vụ các ngoại lệ khác nhau.

**“`finally` giúp sửa lỗi biên dịch vì nó luôn chạy.”** Sai. `finally` không đổi quy tắc thứ tự và kiểu của `catch`.

## 13. Bài tập tự kiểm tra

### Bài 1: Thứ tự hai kiểu có quan hệ kế thừa

```java
try {
    read();
} catch (java.io.IOException e) {
    System.out.println("io");
} catch (java.io.FileNotFoundException e) {
    System.out.println("missing");
}
```

**Câu hỏi:** Có biên dịch được không? **Đáp án:** Không. `FileNotFoundException` là lớp con của `IOException`, nên handler phía dưới bị bao phủ. Đưa `FileNotFoundException` lên trước. Đây là mảnh minh họa, cần khai báo `read()`.

### Bài 2: Hai lớp anh em

```java
try {
    process();
} catch (java.io.IOException e) {
    System.out.println("io");
} catch (java.sql.SQLException e) {
    System.out.println("sql");
}
```

**Câu hỏi:** Có lỗi bao phủ do thứ tự không? **Đáp án:** Không. Hai kiểu checked exception khác nhánh. Để đoạn mã biên dịch đầy đủ, `process()` phải có đường có thể ném checked exception tương ứng. Đây là đoạn minh họa, chưa phải chương trình độc lập.

### Bài 3: Runtime exception

```java
try {
    System.out.println(Integer.parseInt("x"));
} catch (RuntimeException e) {
    System.out.println("runtime");
} catch (NumberFormatException e) {
    System.out.println("format");
}
```

**Câu hỏi:** Có thể vào `catch (NumberFormatException)` không? **Đáp án:** Không. `NumberFormatException` là một `RuntimeException`. Đưa handler cụ thể lên trên hoặc chỉ giữ handler rộng khi hợp đồng xử lý giống nhau.

### Bài 4: Lỗi không được ném từ `try`

```java
try {
    System.out.println("hello");
} catch (java.io.IOException e) {
    System.out.println("io");
}
```

**Câu hỏi:** Đây có phải lỗi do `catch` trước bao phủ? **Đáp án:** Không. `try` không thể ném checked `IOException` từ các thao tác hiển thị, nên handler không phù hợp với kiểm tra ngoại lệ tại compile time.

### Bài 5: `Throwable` rồi `Error`

```java
try {
    throw new AssertionError("assertion");
} catch (Throwable e) {
    System.out.println("anything");
} catch (Error e) {
    System.out.println("error");
}
```

**Câu hỏi:** Điều gì xảy ra? **Đáp án:** Lỗi biên dịch vì `Throwable` đứng trước đã bao phủ `Error`. Không nên “sửa” bằng cách bắt `Throwable` rộng hơn nữa; cần xác định có thực sự cần xử lý `Error` hay không.

## 14. Tài liệu Java 17

- [Java Language Specification 17 — `14.20.1 Execution of try-catch](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.1): chọn handler tương thích theo thứ tự xuất hiện.
- [Java Language Specification 17 — `11.2.3 Exception Checking](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.2.3): quy tắc compile-time về checked exception và handler.
- [Java Language Specification 17 — `14.21 Unreachable Statements](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.21): tính khả đạt của câu lệnh.
- [Java Language Specification 17 — `14.20 The try Statement](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20): cú pháp `catch`, multi-catch và `finally`.
- [Java SE 17 API — IOException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/IOException.html), [FileNotFoundException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/FileNotFoundException.html): quan hệ kiểu cha–con.
- [Java SE 17 API — RuntimeException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/RuntimeException.html), [Exception](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Exception.html): các kiểu handler rộng.

**Kết luận học tập:** Khi một `catch` của cùng `try` đã bao phủ toàn bộ kiểu ngoại lệ mà một `catch` phía sau định nhận, handler phía sau không thể tới được và Java báo lỗi biên dịch. Hãy đối chiếu quan hệ kế thừa, đặt handler cụ thể trước handler tổng quát, kiểm tra riêng các điều kiện của checked exception và multi-catch rồi mới chạy chương trình.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="try-catch-010">Quy tắc bắt checked exception mà khối try không thể ném</a>

<details>
<summary>Xem chi tiết</summary>

# Quy tắc bắt checked exception mà khối `try` không thể ném trong Java

> **Chủ đề:** Java Core → Exception → Try và catch. Java không cho phép khai báo tùy ý một `catch` dành cho **checked exception cụ thể** nếu phân tích tại thời điểm biên dịch cho thấy khối `try` tương ứng không thể ném ra một checked exception tương thích. Đây là quy tắc về **tính hợp lệ của chương trình ở compile-time**, khác với câu hỏi “ngoại lệ có thực sự xảy ra ở một lần chạy hay không”. Quy tắc có ngoại lệ quan trọng cho `catch (Exception)` và các kiểu cha của `Exception`, điển hình `catch (Throwable)`.

## 1. Câu trả lời cốt lõi

Xét ví dụ trong một phương thức Java:

```java
try {
    int value = Integer.parseInt("123");
    System.out.println(value);
} catch (java.io.IOException e) {
    System.out.println("Lỗi I/O");
}
```

Đoạn trên **không biên dịch**. `Integer.parseInt(String)` không khai báo ném checked `IOException`; `System.out.println` cũng không công bố checked `IOException`. `Integer.parseInt` có thể ném unchecked `NumberFormatException`, nhưng exception đó không thuộc nhánh `IOException`.

Compiler có thể báo tương tự:

```text
exception IOException is never thrown in body of corresponding try statement
```

Thông báo chính xác phụ thuộc compiler/JDK; không coi đây là stdout của chương trình.

Nếu muốn bắt lỗi parse số, dùng handler phù hợp:

```java
try {
    int value = Integer.parseInt("abc");
    System.out.println(value);
} catch (NumberFormatException e) {
    System.out.println("Giá trị nhập không phải số nguyên");
}
```

Đoạn sửa biên dịch được; ở trường hợp `"abc"` nhánh `NumberFormatException` được chọn.

**Quy tắc thực hành:** với một checked exception cụ thể như `IOException`, `SQLException`, `ParseException`, `InterruptedException` hay lớp con tự tạo của `Exception`, compiler đòi hỏi vùng `try` có khả năng ném một checked exception mà handler có thể bắt, trừ ngoại lệ đặc biệt của ngôn ngữ nêu ở Mục 4.

## 2. Thế nào là một checked exception?

Mọi đối tượng được ném trong Java thuộc hệ `java.lang.Throwable`. Hai nhánh lớn:

```text
java.lang.Throwable
├── java.lang.Error
│   └── AssertionError
└── java.lang.Exception
    ├── java.io.IOException
    │   ├── java.io.FileNotFoundException
    │   └── java.io.EOFException
    ├── java.text.ParseException
    ├── java.lang.InterruptedException
    └── java.lang.RuntimeException
        ├── java.lang.IllegalArgumentException
        │   └── java.lang.NumberFormatException
        └── java.lang.NullPointerException
```

Các loại được phân biệt:

- **Unchecked exception:** `RuntimeException` và các lớp con, `Error` và các lớp con. Code có thể ném chúng dù không khai báo `throws`; compiler không buộc caller bắt hoặc khai báo.
- **Checked exception:** các lớp con khác của `Throwable` nằm ngoài hai nhánh `RuntimeException` và `Error`. Ví dụ `IOException`, `Exception`, `Throwable` và một lớp trực tiếp `extends Throwable`. Khi chúng có thể thoát ra, quy tắc catch-or-declare áp dụng.
- **`Exception` không đồng nghĩa với checked:** `NumberFormatException` là một `Exception` nhưng vẫn unchecked do kế thừa `RuntimeException`.

Cần phân biệt **hai** phép kiểm tra:

1. Khi một phương thức có thể để checked exception thoát ra, caller thường phải **`catch` hoặc khai báo `throws`** phù hợp.
2. Khi một `catch` khai báo một kiểu checked cụ thể, compiler cũng kiểm tra vùng `try` có **khả năng ném ngoại lệ checked tương thích** hay không.

Bài này tập trung vào phép kiểm tra thứ hai, đồng thời giải thích những trường hợp dễ hiểu nhầm.

## 3. Chính xác compiler kiểm tra điều gì?

Theo quy tắc `try`/`catch` của Java Language Specification (JLS `14.20), với một kiểu checked `E` mà handler có thể bắt, chương trình có thể bị lỗi nếu vùng `try` không thể ném một checked exception có quan hệ kiểu phù hợp với `E`. Có **ngoại lệ đặc biệt** khi `E` là `Exception` hoặc một **lớp cha của `Exception`**.

Diễn đạt dễ sử dụng:

- `catch (IOException e)` yêu cầu vùng `try` có đường có thể ném loại checked liên quan (chẳng hạn `IOException`, subtype như `FileNotFoundException`, hoặc kiểu khai báo rộng như `Exception` có thể chứa `IOException`).
- `catch (ParseException e)` không hợp lệ nếu vùng `try` chỉ gọi những phương thức không khai báo kiểu checked tương thích và không tự ném một kiểu như thế.
- `catch (RuntimeException e)` hợp lệ dù vùng `try` không có dòng nào “rõ ràng” ném unchecked exception.
- `catch (Exception e)` hợp lệ ngay cả khi vùng `try` không khai báo phát sinh checked exception.
- `catch (Throwable t)` cũng hợp lệ trong trường hợp tương tự, nhưng bắt rộng như vậy thường không thích hợp ở tầng nghiệp vụ.

**Đây không phải phân tích chứng minh chắc chắn ngoại lệ xảy ra ở runtime.** Compiler chủ yếu dựa vào các kiểu ngoại lệ mà các biểu thức, lời gọi, resource và cấu trúc ngôn ngữ **có thể ném** theo hợp đồng tĩnh, cùng các quy tắc phân tích luồng đặc thù. Một method khai báo `throws IOException` có thể luôn trả về bình thường trong implementation hiện tại, nhưng caller vẫn có thể viết `catch (IOException)` hợp lệ.

### 3.1. Bảng dự đoán khả năng biên dịch

Xét từng cặp vùng `try`/`catch` độc lập:

| Code trong `try` | Handler | Kết quả | Lý do |
| --- | --- | --- | --- |
| `System.out.println("Hi");` | `catch (IOException e)` | **Lỗi biên dịch** | Không có checked `IOException` phù hợp từ vùng `try`. |
| `Integer.parseInt("abc");` | `catch (NumberFormatException e)` | Hợp lệ | `NumberFormatException` là unchecked; có thể bắt. |
| `System.out.println("Hi");` | `catch (RuntimeException e)` | Hợp lệ | Unchecked exception không bị giới hạn bởi quy tắc catch checked cụ thể. |
| `System.out.println("Hi");` | `catch (Exception e)` | Hợp lệ | `Exception` là trường hợp được JLS cho phép đặc biệt. |
| `System.out.println("Hi");` | `catch (Throwable t)` | Hợp lệ | `Throwable` là lớp cha của `Exception`. |
| `readFromFile();` với `throws IOException` | `catch (IOException e)` | Hợp lệ | Chữ ký method con công bố đường có thể ném `IOException`. |
| `readFromFile();` với `throws IOException` | `catch (FileNotFoundException e)` | Có thể hợp lệ nếu các handler còn lại/khai báo bao phủ checked exceptions | `FileNotFoundException` là subtype có thể nằm trong `IOException` công bố. |
| `readFromFile();` với `throws IOException` | `catch (SQLException e)` | Lỗi nếu không có nguồn SQLException khác | Hai nhánh checked khác nhau, không tương thích. |

Trong trường hợp handler chỉ bắt subtype `FileNotFoundException` từ method khai báo `throws IOException`, compiler vẫn cần code giải quyết **phần IOException còn lại** bằng handler tiếp theo hoặc `throws IOException` của method bao quanh. Khả năng handler tồn tại hợp lệ không tự có nghĩa đã xử lý đầy đủ mọi checked exception.

## 4. Vì sao `catch (Exception)` và `catch (Throwable)` thường không bị lỗi “never thrown”?

Đây là ngoại lệ quan trọng cần nhớ chính xác:

```java
try {
    System.out.println("Hello");
} catch (Exception e) {
    System.out.println("Có Exception");
}
```

Đoạn này **hợp lệ về mặt biên dịch**, dù `println` không khai báo ném một checked `Exception`. `catch (Exception)` có phạm vi bao gồm cả `RuntimeException`; quan trọng hơn, JLS có ngoại lệ trực tiếp cho `Exception` và các lớp cha của nó trong kiểm tra checked exception “not thrown”. `catch (Throwable)` cũng nằm trong ngoại lệ này.

Đặt cạnh đó:

```java
try {
    System.out.println("Hello");
} catch (java.io.IOException e) {
    System.out.println("Có IOException");
}
```

Lại **không hợp lệ**, vì `IOException` là checked subtype cụ thể và vùng `try` không có khả năng ném checked exception tương thích.

Đừng suy luận rằng vì `Exception` là checked nên `catch (Exception)` phải bị cấm. Đây là một **ngoại lệ được định nghĩa trong quy tắc biên dịch**, không phải lỗ hổng của compiler. Tương tự, sự hợp lệ của `catch (Throwable)` **không** đồng nghĩa nó là handler an toàn cho chương trình: nó có thể bắt cả `Error` mà code nghiệp vụ không nên nuốt.

### 4.1. Hợp lệ về cú pháp chưa có nghĩa là handler sẽ chạy

```java
try {
    System.out.println("OK");
} catch (Exception e) {
    System.out.println("Không mong đợi");
}
```

Chỉ với ví dụ trên, stdout bình thường là:

```text
OK
```

Handler không chạy vì không có `Exception` nào được ném trong lần chạy đó. Java cho phép khai báo handler này, nhưng không tự tạo lỗi để kích hoạt nó.

### 4.2. Không lẫn với handler bị che bởi loại cha

Đoạn sau **vẫn sai**, dù `catch (Exception)` đứng trong trường hợp đặc biệt:

```java
try {
    System.out.println("Hello");
} catch (Exception e) {
    System.out.println("Exception");
} catch (RuntimeException e) {
    System.out.println("RuntimeException");
}
```

Handler `RuntimeException` đứng sau đã bị `Exception` bao phủ, nên compiler báo handler không thể tới được. **Hai quy tắc riêng biệt** cùng tồn tại:

- Quy tắc “try có thể ném checked exception mà catch nhận hay không?”
- Quy tắc “handler này có bị handler đứng trước bao phủ hoàn toàn hay không?”

Dù được miễn quy tắc thứ nhất, `catch (Exception)` vẫn có thể làm các handler phía sau trở thành unreachable.

## 5. Chương trình Java 17: ba handler hợp lệ, dù không có checked exception

Tạo tệp `LegalCatchWithoutCheckedDemo.java`:

```java
public class LegalCatchWithoutCheckedDemo {
    static void inspect(boolean bad) {
        try {
            int value = bad
                    ? Integer.parseInt("không-phải-số")
                    : Integer.parseInt("20");
            System.out.println("Giá trị: " + value);
        } catch (NumberFormatException e) {
            System.out.println("Lỗi parse: "
                    + e.getClass().getSimpleName());
        } catch (Exception e) {
            System.out.println("Exception khác: "
                    + e.getClass().getSimpleName());
        } finally {
            System.out.println("Hoàn tất lượt kiểm tra");
        }
    }

    public static void main(String[] args) {
        inspect(false);
        inspect(true);
    }
}
```

Lệnh biên dịch và chạy:

```bash
javac -encoding UTF-8 LegalCatchWithoutCheckedDemo.java
java LegalCatchWithoutCheckedDemo
```

Stdout chính xác:

```text
Giá trị: 20
Hoàn tất lượt kiểm tra
Lỗi parse: NumberFormatException
Hoàn tất lượt kiểm tra
```

Tại sao hai `catch` hợp lệ?

1. `NumberFormatException` thuộc nhánh unchecked `RuntimeException`.
2. `catch (Exception)` được phép dù vùng `try` không có checked exception nào được khai báo.
3. Handler hẹp `NumberFormatException` đặt trước handler rộng `Exception` để tránh lỗi unreachable.
4. `finally` thường được thực hiện sau mỗi lượt, kể cả khi có exception được bắt. Trong ví dụ này nó in một dòng sau cả hai tình huống.

Chương trình không có checked exception ở đường parse; không cần `throws IOException` hay một `catch IOException` giả tạo.

## 6. Trường hợp sai: bắt `IOException` khi `try` chỉ xử lý dữ liệu trong bộ nhớ

Ví dụ sau là một chương trình **cố ý không biên dịch**; lưu thành `InvalidCheckedCatchDemo.java` chỉ khi bạn muốn quan sát chẩn đoán compiler:

```java
import java.io.IOException;

public class InvalidCheckedCatchDemo {
    public static void main(String[] args) {
        try {
            int number = Integer.parseInt("123");
            System.out.println(number);
        } catch (IOException e) {
            System.out.println("I/O thất bại");
        }
    }
}
```

Chạy lệnh kiểm tra:

```bash
javac -encoding UTF-8 InvalidCheckedCatchDemo.java
```

**Kết quả mong đợi:** `javac` không tạo chương trình hợp lệ vì `IOException` không được ném từ vùng `try` tương ứng. Đường đi của `Integer.parseInt` có thể ném `NumberFormatException` (unchecked), không phải checked `IOException`. Không nên dự báo stdout, vì chương trình không biên dịch.

### 6.1. Sửa theo lỗi thực sự có thể xảy ra

Nếu nhu cầu là xử lý input không parse được, sửa handler:

```java
catch (NumberFormatException e) {
    System.out.println("Không thể chuyển thành số");
}
```

Đây là **đoạn handler để thay thế**, không đứng độc lập ngoài `try`. Nếu muốn đọc dữ liệu từ file, hãy thực sự dùng API đọc có thể ném `IOException` (và cân nhắc try-with-resources) thay vì thêm một handler checked không liên quan.

### 6.2. Không sửa bằng cách viết `throws IOException` trên method chứa `try`

Chỉ thêm:

```java
public static void main(String[] args) throws java.io.IOException {
    // Vùng try chỉ parse số.
}
```

**không làm `catch (IOException)` bên trong tự trở nên hợp lệ**. `throws` trên method ngoài chỉ công bố ngoại lệ có thể **thoát khỏi method**; nó không khiến các câu lệnh bên trong `try` bất ngờ có khả năng ném `IOException`.

Ngược lại, thay đổi **chữ ký của một method con thực sự được gọi trong `try`** có thể làm quy tắc checked catch thay đổi vì vùng `try` nay có một lời gọi khai báo kiểu checked tương ứng.

## 7. Một lời gọi có `throws` làm `catch` hợp lệ dù lần chạy không có lỗi

Tạo `DeclaredThrowsCatchDemo.java`:

```java
import java.io.IOException;

public class DeclaredThrowsCatchDemo {
    static String readConfig(boolean fail) throws IOException {
        if (fail) {
            throw new IOException("Không có cấu hình");
        }
        return "ready";
    }

    public static void main(String[] args) {
        for (boolean fail : new boolean[] {false, true}) {
            try {
                System.out.println("Cấu hình: " + readConfig(fail));
            } catch (IOException e) {
                System.out.println("Lỗi đọc: " + e.getMessage());
            }
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 DeclaredThrowsCatchDemo.java
java DeclaredThrowsCatchDemo
```

Stdout:

```text
Cấu hình: ready
Lỗi đọc: Không có cấu hình
```

- `readConfig` công bố `throws IOException`.
- Ở lượt `false`, không có exception: Java không yêu cầu một handler phải được chạy mới được coi là hợp lệ.
- Ở lượt `true`, `IOException` thực sự được ném: handler tương ứng chạy.
- Khả năng `try` ném checked exception được xét theo lời gọi `readConfig(fail)` và chữ ký của method, không dựa vào việc người lập trình “biết trước” một lần chạy sẽ đi nhánh nào.

### 7.1. Implementation không thật sự ném nhưng vẫn khai báo `throws`

Phương thức có thể viết:

```java
static String cachedValue() throws java.io.IOException {
    return "cached";
}
```

Một caller trực tiếp có thể bắt `IOException` quanh lời gọi `cachedValue()`, dù thân hiện tại chỉ trả về chuỗi. Trình biên dịch caller dựa trên **hợp đồng khai báo của method**. Trong mã sản xuất, nên giữ chữ ký phản ánh khả năng thất bại thực tế hoặc hợp đồng interface, tránh khai báo checked exception rộng vô ích; nhưng hợp lệ về ngôn ngữ không phụ thuộc vào việc một lần chạy đã thực sự ném.

## 8. Quan hệ kế thừa: `try` có thể ném kiểu cha hoặc kiểu con

Không nhất thiết kiểu checked trong `throws` phải **giống hệt** tên trong `catch`.

Ví dụ hợp lệ và có ý nghĩa:

```java
static void load() throws java.io.IOException {
    throw new java.io.FileNotFoundException("Thiếu file");
}
```

Caller có thể dùng:

```java
try {
    load();
} catch (java.io.FileNotFoundException e) {
    System.out.println("Chọn tệp khác");
} catch (java.io.IOException e) {
    System.out.println("I/O khác");
}
```

Các đoạn trên là minh họa phương thức và lời gọi; cần đặt trong lớp/method đúng ngữ cảnh. Dù `load()` khai báo `throws IOException`, thực tế có thể ném `FileNotFoundException` — một lớp con. Handler con đứng trước handler cha là hợp lệ. Handler `IOException` còn lại xử lý mọi `IOException` khác có thể phát sinh theo hợp đồng.

### 8.1. Catch subtype của loại checked được khai báo rộng

Giả sử `read() throws IOException`; có thể viết `catch (EOFException)` trước `catch (IOException)` dù chữ ký chỉ nói `IOException`. Lý do: một đối tượng thuộc `EOFException` cũng là `IOException`, nên đường checked tương ứng **không bị loại trừ bởi khai báo kiểu cha**.

Tương tự, nếu `operation() throws Exception`, vùng `try` có thể bị xem là có khả năng ném `IOException` hoặc `ParseException` ở mức kiểu, nhưng **caller vẫn phải xử lý phần `Exception` còn lại**. Không thể chỉ viết handler cho hai subtype rồi coi `throws Exception` đã được xử lý hết.

### 8.2. Catch loại checked không có quan hệ kế thừa sẽ bị lỗi

Nếu vùng `try` chỉ gọi `read() throws IOException`, thì `catch (java.sql.SQLException e)` không hợp lệ khi không có nguồn checked `SQLException` khác trong vùng `try`. Cả hai cùng kế thừa `Exception` không đủ: `IOException` và `SQLException` là hai nhánh khác nhau, nên ném một `IOException` không tạo khả năng ném `SQLException`.

**Mẹo:** đọc chuỗi tổ tiên của kiểu lỗi. Handler `catch (Parent)` bắt `Child` nếu `Child extends Parent`. Ngược lại, khi phương thức **khai báo một kiểu cha** trong `throws`, compiler có thể cho phép handler subtype vì một đối tượng thuộc subtype vẫn thỏa kiểu cha đã khai báo.

## 9. Try-with-resources: `close()` cũng là nguồn checked exception

Một trường hợp rất dễ bỏ sót: ngay cả khi **thân `try` không tự đọc hay ném checked exception**, thao tác **đóng resource** trong try-with-resources có thể khai báo checked exception. Khi đó handler checked là hợp lệ.

Tạo `ResourceCloseCatchDemo.java`:

```java
import java.io.IOException;

public class ResourceCloseCatchDemo {
    static final class Resource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            System.out.println("Đang đóng");
            throw new IOException("Đóng thất bại");
        }
    }

    public static void main(String[] args) {
        try (Resource resource = new Resource()) {
            System.out.println("Đang thực hiện");
        } catch (IOException e) {
            System.out.println("Lỗi tài nguyên: " + e.getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 ResourceCloseCatchDemo.java
java ResourceCloseCatchDemo
```

Stdout chính xác:

```text
Đang thực hiện
Đang đóng
Lỗi tài nguyên: Đóng thất bại
```

Vì sao `catch (IOException)` hợp lệ dù thân khối chỉ có `println`?

- Resource `Resource` triển khai `AutoCloseable` và `close() throws IOException`.
- Try-with-resources thực thi `close()` khi rời khối.
- `close()` có thể ném checked `IOException`, nên toàn bộ construct có đường lỗi phù hợp với handler.
- Exception do `close()` được bắt ở `catch` gắn với try-with-resources đó.

Với nhiều resource, Java đóng theo thứ tự ngược thứ tự khai báo. Nếu thân khối đã ném exception và `close()` cũng ném, lỗi đóng thường được ghi vào `getSuppressed()` của exception chính, thay vì tự thay thế ngoại lệ đầu tiên.

### 9.1. Không phải mọi `close()` đều khai báo IOException

Ví dụ `StringReader.close()` không khai báo checked `IOException`; tạo `StringReader` với chuỗi và chỉ gọi `println` trong thân khối sẽ không tự khiến `catch (IOException)` hợp lệ chỉ vì tên cấu trúc là try-with-resources.

Điều quan trọng là **kiểu và phương thức `close()` thực tế được compiler nhìn thấy**, cùng các constructor/initializer và thao tác trong thân khối. Nếu khai báo resource dưới kiểu rộng `AutoCloseable`, `close()` trong interface công bố `throws Exception`; điều đó có thể tạo nghĩa vụ checked rộng hơn so với dùng kiểu cụ thể có chữ ký `close()` hẹp hơn.

### 9.2. Lỗi từ resource initializer cũng được xét

Phần khởi tạo resource trong `try ( ... )` có thể gọi constructor hoặc phương thức khai báo checked exception. Những lỗi checked phát sinh trong quá trình **tạo resource** cũng thuộc đường lỗi cần xử lý theo quy tắc ngôn ngữ; không chỉ có các câu lệnh trong cặp ngoặc nhọn.

## 10. Multi-catch: mỗi lựa chọn checked vẫn phải có lý do hợp lệ

Cú pháp multi-catch:

```java
catch (java.io.IOException | IllegalArgumentException e) {
    System.out.println(e.getMessage());
}
```

Đây là một handler minh họa, cần đi sau `try`. Một multi-catch có thể gộp nhiều kiểu không có quan hệ kế thừa subtype với nhau, khi chúng được xử lý giống nhau.

Tuy nhiên, việc thêm một nhánh unchecked **không hợp thức hóa** nhánh checked không thể xảy ra:

```java
// CỐ Ý SAI NẾU TRY CHỈ PARSE SỐ:
try {
    Integer.parseInt("123");
} catch (java.io.IOException | NumberFormatException e) {
    System.out.println("Có lỗi");
}
```

`NumberFormatException` là unchecked, phù hợp. Nhưng `IOException` là checked và `try` không thể ném nó, nên cấu trúc trên không hợp lệ.

Cách sửa dựa vào nguồn lỗi có thật:

- Nếu chỉ parse số, bỏ `IOException` khỏi multi-catch.
- Nếu `try` thực sự gọi I/O có `throws IOException`, khi ấy multi-catch có thể hợp lệ nếu các kiểu không có quan hệ subclass với nhau và logic xử lý phù hợp.

**Không được** gộp `IOException | FileNotFoundException` trong một multi-catch, vì `FileNotFoundException extends IOException`. Đây là quy tắc khác với “checked exception không được ném”, nhưng cũng gây lỗi compile-time.

### 10.1. Chương trình Java 17 với hai loại thực sự có thể xảy ra

Tạo `MultiCatchCheckedDemo.java`:

```java
import java.io.IOException;

public class MultiCatchCheckedDemo {
    static void handle(String text) throws IOException {
        if (text == null) {
            throw new IOException("Thiếu nguồn");
        }
        System.out.println("Số: " + Integer.parseInt(text));
    }

    public static void main(String[] args) {
        for (String text : new String[] {"8", "abc", null}) {
            try {
                handle(text);
            } catch (IOException | NumberFormatException e) {
                System.out.println("Lỗi: "
                        + e.getClass().getSimpleName());
            }
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 MultiCatchCheckedDemo.java
java MultiCatchCheckedDemo
```

Stdout:

```text
Số: 8
Lỗi: NumberFormatException
Lỗi: IOException
```

Đây là trường hợp multi-catch hợp lệ: `handle` công bố `throws IOException`, còn `Integer.parseInt` bên trong có thể phát sinh unchecked `NumberFormatException`. Hai kiểu không có quan hệ cha–con, và cùng dùng một chiến lược hiển thị lỗi.

Biến exception của multi-catch là **implicitly final**; không thể gán lại nó. Khi cần logic xử lý khác nhau cho hai loại, tách ra hai handler riêng giúp hợp đồng rõ hơn.

## 11. Nested `try`, `catch` đã xử lý và `finally`

Câu hỏi “khối `try` có thể ném loại checked nào?” phải đặt trong **phạm vi chính xác** mà handler đang gắn vào.

### 11.1. Exception đã được bắt ở `try` trong

Đoạn minh họa:

```java
try {
    try {
        throw new java.io.IOException("Đã có lỗi");
    } catch (java.io.IOException handled) {
        System.out.println("Đã xử lý ở trong");
    }
} catch (java.io.IOException e) {
    System.out.println("Bắt ở ngoài");
}
```

Đoạn trên **cố ý không biên dịch** vì IOException đã được xử lý bên trong, không còn đường checked IOException truyền ra khỏi thân `try` ngoài. Handler ở ngoài không thể nhận IOException từ tình huống này.

Nếu handler trong `throw handled;` hoặc khối try ngoài thực sự gọi thao tác khác có thể ném IOException, việc đánh giá sẽ khác. Compiler xét khả năng exception có thể **thoát khỏi** cấu trúc con, không đơn giản đếm số chữ `throw` xuất hiện ở mọi cấp.

### 11.2. Exception mới ném trong một `catch` cùng cấp

Xét:

```java
try {
    Integer.parseInt("abc");
} catch (NumberFormatException e) {
    throw new java.io.IOException("Lỗi chuyển đổi", e);
} catch (java.io.IOException e) {
    System.out.println("Không bắt lỗi vừa ném từ catch bên trên");
}
```

Mảnh trên **cố ý không biên dịch** nếu chỉ có nguồn lỗi như đã nêu: handler `IOException` cuối không nhận exception mới ném trong `catch` khác thuộc cùng `try`; đồng thời method bao quanh phải xử lý/khai báo checked IOException mà handler đầu tạo ra.

Một ngoại lệ mới được ném trong handler cần được xử lý bởi một **`try` mới hoặc phạm vi bên ngoài**, hoặc phải được khai báo tiếp. Các `catch` của cùng một `try` là những nhánh thay thế, không phải chuỗi handler tuần tự.

### 11.3. Exception phát sinh trong `finally`

Một ngoại lệ được ném trong `finally` của cùng construct không được handler `catch` kèm theo xử lý như thể nó đến từ thân `try`. Nó có thể lan lên tầng gọi hoặc handler bên ngoài.

Ví dụ phương thức minh họa:

```java
static void cleanup() throws java.io.IOException {
    try {
        System.out.println("Làm việc");
    } finally {
        throw new java.io.IOException("Lỗi khi dọn dẹp");
    }
}
```

Method phải khai báo `throws IOException`; không thể thêm `catch (IOException)` ở ngay chuỗi handler của `try` cùng cấp để xử lý exception sinh ra từ `finally` đó. Muốn xử lý tại đây, dùng `try` lồng phù hợp ở vị trí dọn dẹp hoặc để caller xử lý theo hợp đồng.

**Đừng nhầm `finally` với try-with-resources:** với try-with-resources, các lỗi checked do `close()` có thể được xử lý bởi `catch` gắn với cấu trúc đó.

## 12. Lý do compiler cần quy tắc này

Một `catch (IOException)` nằm sau `try` chỉ tính toán số nguyên thường biểu đạt sai hợp đồng: code ở vùng `try` không thao tác với nguồn I/O theo thông tin kiểu tĩnh. Quy tắc giúp phát hiện:

1. **Sai loại exception:** người viết nhầm `NumberFormatException` với `IOException`.
2. **Refactor chưa cập nhật:** một lời gọi I/O bị thay bằng tính toán trong RAM nhưng handler cũ vẫn tồn tại.
3. **Hiểu nhầm `throws`:** người viết tưởng thêm `throws` ở method bao ngoài khiến một `catch` bất kỳ hợp lệ.
4. **Nhầm phạm vi:** exception phát sinh trong `catch` hoặc `finally`, nhưng người viết mong handler cùng cấp nhận được.
5. **Multi-catch thừa loại checked:** gom cả những lỗi không có nguồn phát sinh.

Mặt khác, do quy tắc dựa vào **khả năng theo static type** (không phải kết quả chắc chắn mỗi lần chạy), compiler vẫn có thể cho phép handler không bao giờ thực sự chạy, chẳng hạn method con khai báo `throws IOException` nhưng implementation đang luôn trả thành công. Đó là cách chữ ký và tương thích API được duy trì.

## 13. Phân biệt với các lỗi biên dịch gần giống

| Lỗi | Ví dụ ý tưởng | Bản chất |
| --- | --- | --- |
| “exception X is never thrown in body of corresponding try” | `catch(IOException)` sau parse số thuần | Handler checked cụ thể không có nguồn tương thích. |
| “unreported exception X; must be caught or declared” | Gọi `readLine()` không bắt/khai báo `IOException` | Một checked exception có thể thoát nhưng chưa được xử lý đúng. |
| “exception X has already been caught” | `catch(Exception)` đứng trước `catch(IOException)` | Handler trước che phủ handler sau. |
| “Alternatives in a multi-catch statement cannot be related by subclassing” | `catch(IOException \| FileNotFoundException)` | Hai kiểu trong cùng multi-catch có quan hệ cha–con. |
| “unreachable statement” | Câu lệnh bình thường ngay sau một `throw` vô điều kiện | Luồng thực thi không thể tới câu lệnh đó. |
| “incompatible types ... cannot be converted to Throwable” | `throw "error";` | Biểu thức sau `throw` không phải kiểu throwable phù hợp. |

Khi gặp lỗi, đọc **cụm từ trọng tâm** và kiểm tra quan hệ kiểu giữa nguồn gây lỗi trong `try` và handler; đừng sửa bằng cách thêm `catch (Exception)` ở mọi nơi nếu không biết phần nào cần xử lý.

## 14. Các sai lầm phổ biến và cách sửa

### 14.1. “Bất kỳ `catch` nào cũng hợp lệ vì JVM có thể gặp lỗi bất ngờ”

Không đúng với checked exception cụ thể. Một nguồn có thể ném unchecked `RuntimeException` hoặc `Error`, nhưng điều đó không tạo ra khả năng ném `IOException` theo hợp đồng checked. `catch(IOException)` vẫn cần cơ sở trong vùng `try`.

### 14.2. “`catch (Exception)` phải bị cấm nếu `try` không ném checked exception”

Không đúng. `catch (Exception)` được ngôn ngữ cho phép đặc biệt; nó cũng có thể xử lý unchecked `RuntimeException`. Tương tự `catch (Throwable)` vẫn biên dịch nhưng thường quá rộng cho tầng nghiệp vụ.

### 14.3. “Viết một `throws IOException` ở method hiện tại là đủ để catch IOException”

Không. `throws` mô tả checked exception **thoát khỏi method**, không tự đổi nội dung hoặc khả năng ném của `try` con. Đọc chính xác method nào được gọi bên trong `try` và chữ ký của nó.

### 14.4. “Nếu code chỉ tạo `FileNotFoundException` thì `catch(IOException)` bị cấm vì tên khác”

Sai. `FileNotFoundException extends IOException`; handler kiểu cha bắt subtype được. Quy tắc compiler cho phép quan hệ kiểu phù hợp, không đòi tên giống hệt.

### 14.5. “Nếu `try` gọi method throws IOException, `catch(SQLException)` cũng hợp lệ vì cùng checked”

Sai. Hai nhánh không liên quan; không có nguồn checked SQLException được công bố nên handler riêng đó không hợp lệ trong hoàn cảnh đã nêu.

### 14.6. “Try-with-resources chỉ xét lệnh trong cặp ngoặc nhọn”

Sai. Resource initializer và `close()` có thể ném checked exception. `catch(IOException)` có thể hoàn toàn hợp lệ khi thân khối chỉ in một chuỗi, nếu resource `close()` khai báo `throws IOException`.

### 14.7. “Checked exception xuất hiện ở nested try thì outer catch luôn hợp lệ”

Sai. Nếu inner `catch` đã xử lý xong và không ném lại, checked exception tương ứng có thể không thoát ra vùng `try` ngoài. Phải xét **đường truyền thực sự theo cấu trúc**, không chỉ tìm chuỗi `throw IOException` trong file.

### 14.8. “Exception được ném trong `catch` sẽ được handler kế tiếp bắt”

Sai. Các handler cùng cấp là các lựa chọn thay thế cho ngoại lệ thoát khỏi **thân try**. Exception mới trong `catch` phải được xử lý ở một phạm vi khác hoặc khai báo truyền tiếp.

### 14.9. “Compiler luôn chứng minh method con có thể ném thật hay không”

Không. Compiler dùng chữ ký của method con và các quy tắc exception analysis. Một implementation có `throws IOException` dù thân chỉ `return` vẫn đủ tạo khả năng checked cho caller. Đây là cơ chế tương thích khi dùng thư viện/interface.

### 14.10. “Sửa lỗi biên dịch bằng cách nuốt mọi exception”

Không nên. Xóa `IOException` không liên quan khỏi handler khi nguồn lỗi là parse số; hoặc đặt handler ở tầng thực sự xử lý I/O. Bắt rồi bỏ qua lỗi có thể khiến caller hiểu nhầm chương trình đã hoàn tất, còn trạng thái có thể đã thay đổi một phần.

## 15. Bài tập tự kiểm tra

### Bài tập 1 — Hợp lệ hay không?

Đánh giá độc lập các đoạn `try/catch` sau (đặt trong một method thích hợp).

**A.**

```java
try {
    System.out.println(10 / 2);
} catch (java.io.IOException e) {
    System.out.println("I/O");
}
```

**Đáp án:** không hợp lệ. Vùng `try` không có nguồn checked IOException.

**B.**

```java
try {
    System.out.println(10 / 2);
} catch (Exception e) {
    System.out.println("Exception");
}
```

**Đáp án:** hợp lệ; `Exception` là ngoại lệ đặc biệt của quy tắc.

**C.**

```java
try {
    Integer.parseInt("not-a-number");
} catch (NumberFormatException e) {
    System.out.println("Bad number");
}
```

**Đáp án:** hợp lệ; `NumberFormatException` là unchecked. Khi chạy, in `Bad number`.

**D.**

```java
try {
    Thread.sleep(1);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
}
```

**Đáp án:** hợp lệ; `Thread.sleep` khai báo checked `InterruptedException`. Khi bắt interruption, khôi phục interrupt flag có thể phù hợp với chính sách dừng tác vụ; ví dụ không có stdout ổn định vì phụ thuộc thread interruption.

### Bài tập 2 — Thay lời gọi và quan sát compiler

Tạo method `static String read() throws IOException` rồi đặt lời gọi trong `try` có `catch(IOException)`. Sau đó thay lời gọi bằng `return "OK"` hoặc `println("OK")` trong vùng `try`. Vì sao handler checked trở nên không hợp lệ? Việc xóa nguồn khả năng ném checked exception là thay đổi hợp đồng tại phạm vi đó.

### Bài tập 3 — Kiểu cha và kiểu con

Một method khai báo `throws IOException` và bên trong có thể ném `EOFException`. Viết handler `catch(EOFException)` trước `catch(IOException)`. Giải thích lý do cả hai hợp lệ và handler thứ hai còn cần để xử lý các loại IOException khác.

### Bài tập 4 — Resource đóng lỗi

Chạy `ResourceCloseCatchDemo`, sau đó sửa `Resource.close()` để không khai báo/ném IOException và chỉ in “Đang đóng”. Có còn nguồn checked IOException nào trong `try` không? Nếu không, `catch(IOException)` trở thành không hợp lệ. Hãy sửa handler để chương trình biên dịch lại mà vẫn đóng tài nguyên.

### Bài tập 5 — Multi-catch

Tại sao `catch(IOException | NumberFormatException)` hợp lệ trong `MultiCatchCheckedDemo` nhưng lại sai khi vùng `try` chỉ gọi `Integer.parseInt(...)`? Giải thích riêng hai ý: nguồn checked IOException và unchecked NumberFormatException.

### Bài tập 6 — Exception đã được xử lý ở trong

Dùng nested `try` ở Mục 11.1. Thêm `throw handled;` trong inner handler (và khai báo hoặc xử lý hợp lệ ở phạm vi ngoài). Lý giải vì sao việc ném lại làm outer `catch(IOException)` nhận được ngoại lệ, thay vì inner handler hoàn tất bình thường.

## 16. Checklist trước khi thêm một checked `catch`

- [ ] Tôi biết exception cần bắt là checked hay unchecked chưa?
- [ ] Có lời gọi, `throw` hoặc resource nào trong **đúng vùng try** có thể ném checked exception tương thích không?
- [ ] Đã xét initializer và `close()` của try-with-resources chưa?
- [ ] Đã kiểm tra quan hệ cha–con giữa kiểu trong `throws` và kiểu `catch` chưa?
- [ ] Exception có bị xử lý hoàn toàn bởi nested `catch` trước khi tới handler này không?
- [ ] Ngoại lệ có thực ra phát sinh ở `catch`/`finally` cùng cấp, ngoài phạm vi mà handler này bảo vệ không?
- [ ] Có nhầm một unchecked exception từ parser với checked IOException không?
- [ ] Có biết quy tắc đặc biệt của `catch(Exception)` và `catch(Throwable)` mà vẫn cân nhắc phạm vi bắt phù hợp không?
- [ ] Với multi-catch, mọi lựa chọn checked đều có cơ sở và các kiểu không bao phủ nhau chưa?
- [ ] Nếu chỉ cần báo input sai, tôi đã chọn loại ngoại lệ đúng thay vì thêm handler checked không liên quan chưa?
- [ ] Các handler đã theo thứ tự cụ thể trước tổng quát, không có nhánh unreachable chưa?
- [ ] Code có còn phân biệt thất bại và thành công đúng hợp đồng, không nuốt lỗi quan trọng chưa?

**Ghi nhớ:** compiler không cho phép `catch` một **checked exception cụ thể** khi vùng `try` không thể ném checked exception tương thích, với ngoại lệ ngôn ngữ cho `Exception` và các kiểu cha của nó. Hãy xét **nguồn ngoại lệ theo chữ ký kiểu và phạm vi `try`**, rồi mới quyết định handler; tránh tự tạo `catch` thừa hoặc thay mọi lỗi bằng một handler quá rộng.

</details>

- [Quay lại đầu trang](#back-to-top)
