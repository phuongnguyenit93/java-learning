# throw và throws

`throw` và `throws` cùng liên quan tới exception nhưng nằm ở hai thời điểm khác nhau:

```text
throw
→ statement được thực thi ở runtime

throws
→ một phần của method/constructor declaration
```

Mental model:

```text
throw  = "failure xảy ra ngay tại đường chạy này"
throws = "caller cần biết method này có thể để failure này thoát ra"
```

## <a id="throw-statement">throw</a>

`throw` là statement chuyển control flow khỏi đường chạy hiện tại.

```java
if (amount < 0) {
    throw new IllegalArgumentException("amount must be >= 0");
}
```

Sau khi `throw` thực thi:

1. câu lệnh kế tiếp trong block hiện tại không chạy;
2. Java tìm `catch` phù hợp quanh vị trí hiện tại;
3. nếu không có, method hiện tại bị rời bỏ;
4. exception propagate lên caller.

### `throw` không bắt buộc phải tạo object mới

Điều kiện là expression sau `throw` phải có kiểu tương thích với `Throwable`.

Có thể tạo mới:

```java
throw new IOException("cannot read");
```

hoặc ném lại object đã có:

```java
catch (IOException ex) {
    audit(ex);
    throw ex;
}
```

Vì vậy phát biểu chính xác là:

```text
throw <Throwable expression>
```

chứ không phải “sau throw luôn phải là new Exception(...)”.

### Throw và đường chạy bình thường

```java
void validate(int quantity) {
    if (quantity < 0) {
        throw new IllegalArgumentException("quantity < 0");
    }

    save(quantity);
}
```

Nếu `quantity < 0`, `save(quantity)` không được gọi. Exception ở đây vừa mang thông tin lỗi vừa làm control flow rời khỏi đường thành công.

## <a id="throws-clause">throws</a>

`throws` nằm trong declaration:

```java
String load(Path path) throws IOException {
    return Files.readString(path);
}
```

Nó **không tự ném exception**. Nó nói rằng một exception có thể thoát khỏi method.

Với checked exception, `throws` có ý nghĩa compile-time bắt buộc khi method không catch failure:

```java
String load(Path path) throws IOException {
    return Files.readString(path);
}
```

Với unchecked exception, khai báo vẫn hợp lệ nhưng không bắt buộc:

```java
void validate(String value) throws IllegalArgumentException {
    if (value.isBlank()) {
        throw new IllegalArgumentException("blank");
    }
}
```

Thông thường chỉ nên liệt kê unchecked exception trong API documentation/declaration khi nó thực sự làm contract dễ hiểu hơn; đừng thêm mọi `RuntimeException` có thể tưởng tượng.

### Nhiều exception trong throws

```java
void importData(Path path) throws IOException, ParseException {
    ...
}
```

Danh sách nên phản ánh contract có ích cho caller. `throws Exception` quá rộng thường làm mất thông tin:

```text
caller biết "có gì đó fail"
nhưng
không biết nhóm failure nào cần chính sách xử lý nào
```

## <a id="precise-rethrow">Precise Rethrow</a>

Precise rethrow giải quyết một tình huống tinh tế: ta catch bằng một kiểu rộng để làm một việc chung, nhưng compiler vẫn có thể suy luận các checked exception cụ thể thực sự đi vào `catch`.

Ví dụ:

```java
void process() throws IOException, SQLException {
    try {
        readFileOrQueryDatabase();
    } catch (Exception ex) {
        audit(ex);
        throw ex;
    }
}
```

Giả sử code trong `try` chỉ có thể ném `IOException` hoặc `SQLException`. Nếu biến `ex` không bị gán lại, compiler có thể suy luận rethrow chỉ thuộc các checked type đó.

Ta **không bắt buộc** phải đổi declaration thành:

```java
throws Exception
```

### VÌ SAO?

Không có precise rethrow, việc catch một supertype để dùng logic chung có thể làm contract bị rộng không cần thiết.

Mental model:

```text
catch parameter viết rộng
        ↓
compiler phân tích những checked type thực sự có thể tới catch
        ↓
ex không bị thay thế bằng giá trị khác
        ↓
rethrow vẫn giữ được tập type hẹp
```

Multi-catch:

```java
catch (IOException | SQLException ex) {
    throw ex;
}
```

cũng giữ tập type rõ ràng, nhưng đó là **multi-catch**. Precise rethrow đáng chú ý nhất khi parameter được viết bằng một supertype rộng hơn như `Exception` mà compiler vẫn suy luận được tập checked exception cụ thể.

## <a id="override-throws-rules">Quy tắc throws khi Override</a>

Giả sử parent contract là:

```java
class Parent {
    void load() throws IOException {
    }
}
```

Override không được mở rộng checked contract:

```java
class Child extends Parent {
    @Override
    void load() throws Exception { // compile error
    }
}
```

### VÌ SAO?

Code có thể giữ reference kiểu `Parent`:

```java
Parent value = new Child();
value.load();
```

Caller được compile dựa trên contract của `Parent`. Nếu `Child` được phép phát sinh một checked exception rộng hơn, caller có thể gặp checked failure mà contract ban đầu không yêu cầu chuẩn bị.

Đây là một phần của substitutability:

```text
subtype có thể hẹp hơn yêu cầu failure
nhưng không được buộc caller xử lý checked failure rộng hơn contract cha
```

Unchecked exception không chịu giới hạn này theo quy tắc catch-or-declare.

## <a id="checked-exception-narrowing">Thu hẹp Checked Exception</a>

Override có thể:

```text
giữ nguyên checked exception
→ throws IOException

thu hẹp thành subtype
→ throws FileNotFoundException

bỏ checked exception
→ không có throws
```

Ví dụ hợp lệ:

```java
class Child extends Parent {
    @Override
    void load() throws FileNotFoundException {
    }
}
```

hoặc:

```java
class InMemoryChild extends Parent {
    @Override
    void load() {
    }
}
```

Child hứa một contract bằng hoặc dễ xử lý hơn đối với checked failure.

### GHI NHỚ

```text
throw
→ hành động runtime

throws
→ declaration contract

override
→ không được broaden checked contract

precise rethrow
→ catch rộng không nhất thiết làm throws bị rộng
```

Chương tiếp theo theo dõi một exception sau khi được `throw`: Java gỡ call stack ra sao và handler nào cuối cùng được chọn?