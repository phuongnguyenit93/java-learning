# throw, throws và sự lan truyền Exception

`throw` và `throws` cùng liên quan tới Exception nhưng có vai trò khác nhau:

```text
throw
→ câu lệnh được thực thi khi chương trình chạy

throws
→ một phần của khai báo phương thức/constructor
```

Mô hình cần nhớ:

```text
throw  = "ném lỗi ngay tại đường chạy này"
throws = "khai báo rằng lỗi có thể thoát khỏi phương thức"
```

## <a id="throw-statement">throw</a>

`throw` là câu lệnh làm luồng điều khiển rời khỏi đường chạy hiện tại.

```java
if (amount < 0) {
    throw new IllegalArgumentException("amount must be >= 0");
}
```

Sau khi `throw` thực thi:

1. câu lệnh kế tiếp trong khối lệnh hiện tại không chạy;
2. Java tìm `catch` phù hợp quanh vị trí hiện tại;
3. nếu không có, phương thức hiện tại bị rời bỏ;
4. exception tiếp tục lan truyền lên mã gọi.

### `throw` không bắt buộc phải tạo đối tượng mới

Thông thường, biểu thức sau `throw` phải có kiểu tương thích với `Throwable`.

Trường hợp biên của ngôn ngữ là `throw null;`: câu lệnh này vẫn hợp lệ khi biên dịch, nhưng khi chạy Java sẽ phát sinh `NullPointerException` tại chính câu lệnh `throw`.

Có thể tạo mới:

```java
throw new IOException("cannot read");
```

hoặc ném lại đối tượng đã có:

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

Nếu `quantity < 0`, `save(quantity)` không được gọi. Exception ở đây vừa mang thông tin lỗi vừa làm luồng điều khiển rời khỏi đường thành công.

## <a id="throws-clause">throws</a>

`throws` nằm trong phần khai báo:

```java
String load(Path path) throws IOException {
    return Files.readString(path);
}
```

Nó **không tự ném exception**. Nó nói rằng một exception có thể thoát khỏi phương thức.

Với checked exception, `throws` có ý nghĩa bắt buộc tại thời điểm biên dịch khi phương thức không bắt lỗi:

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

Thông thường chỉ nên liệt kê unchecked exception trong tài liệu/khai báo API khi nó thực sự làm hợp đồng dễ hiểu hơn; đừng thêm mọi `RuntimeException` có thể tưởng tượng.

### Nhiều exception trong throws

```java
void importData(Path path) throws IOException, ParseException {
    ...
}
```

Danh sách nên phản ánh hợp đồng có ích cho bên gọi. `throws Exception` quá rộng thường làm mất thông tin:

```text
bên gọi biết "có lỗi xảy ra"
nhưng
không biết nhóm lỗi nào cần chính sách xử lý nào
```

Khi một phương thức không xử lý exception, lỗi không biến mất. Java **lần ngược ngăn xếp lời gọi (stack unwinding)** để tìm một nơi xử lý phù hợp ở phía gọi bên trên.

Đây là cầu nối giữa `throw` và `try/catch`: `throw` tạo ra sự kết thúc bất thường tại một điểm; cơ chế lan truyền quyết định lỗi đó đi đâu tiếp theo.

## <a id="exception-propagation">Lan truyền Exception và lần ngược ngăn xếp</a>

Dùng lại câu chuyện của module:

```text
controller()
   ↓
service()
   ↓
repository()
   ↓
throw IOException
```

Ví dụ:

```java
void controller() throws IOException {
    service();
}

void service() throws IOException {
    repository();
}

void repository() throws IOException {
    throw new IOException("cannot read order");
}
```

Không phương thức nào bắt exception, nên luồng là:

```text
repository()
→ dừng tại throw
→ rời khung ngăn xếp của repository()

service()
→ không tiếp tục sau repository()
→ rời khung ngăn xếp của service()

controller()
→ exception tiếp tục truyền lên bên gọi của controller()
```

Quá trình lần lượt rời từng khung ngăn xếp (stack frame) như vậy là **stack unwinding**.

### Điều gì xảy ra trong lúc unwind?

Khi exception xuất hiện, Java trước hết tìm một khối `catch` phù hợp trong các cấu trúc `try` đang bao quanh vị trí lỗi ở khung hiện tại. Nếu bắt được exception, quá trình lan truyền dừng tại đó.

Nếu không có khối `catch` phù hợp trong khung hiện tại, Java phải rời các phạm vi đang hoạt động trước khi chuyển sang bên gọi:

- `finally` tương ứng vẫn có thể chạy;
- tài nguyên của `try-with-resources` được đóng theo hợp đồng;
- sau các bước dọn dẹp cần thiết, khung hiện tại mới được rời bỏ và quá trình tìm nơi xử lý tiếp tục ở bên gọi.

Ví dụ:

```java
void service() throws IOException {
    try {
        repository();
    } finally {
        System.out.println("service cleanup");
    }
}
```

`repository()` ném `IOException`, nhưng `finally` của `service()` vẫn chạy trước khi exception tiếp tục đi lên.

### Nếu không có ai catch?

Nếu exception thoát khỏi điểm bắt đầu thực thi của thread, nó đi tới cơ chế xử lý ngoại lệ không được bắt (uncaught exception) của thread. Thread đó kết thúc vì luồng thực thi đã hoàn thành theo cách bất thường.

Điều này không đồng nghĩa mọi exception thoát khỏi thread đều lập tức “dừng toàn bộ JVM”; hành vi của tiến trình còn phụ thuộc các thread khác và ngữ cảnh chạy. Điều quan trọng cần nhớ là:

```text
không có khối xử lý
→ lỗi tiếp tục lên tới ranh giới cuối của thread
```

### VÌ SAO CẦN LAN TRUYỀN EXCEPTION?

Tầng repository không cần biết:

- UI sẽ hiển thị thông báo gì;
- yêu cầu HTTP sẽ trả trạng thái nào;
- tác vụ xử lý theo lô (batch job) có thử lại không;
- tầng trên có chuyển đổi exception không.

Nó chỉ báo lỗi. Chính sách xử lý được đặt ở tầng có đủ ngữ cảnh.

## <a id="catch-selection">Chọn `catch` theo kiểu</a>

Khi Java gặp một `try` có nhiều `catch`, nó xét các khối `catch` theo thứ tự xuất hiện và chọn khối đầu tiên có kiểu tham số nhận được exception đang được ném.

```java
try {
    load();
} catch (FileNotFoundException ex) {
    handleMissingFile(ex);
} catch (IOException ex) {
    handleOtherIo(ex);
}
```

`FileNotFoundException` là kiểu con (subtype) của `IOException`, nên nhánh cụ thể phải đứng trước nhánh rộng.

Đoạn này không hợp lệ:

```java
try {
    load();
} catch (IOException ex) {
    handleIo(ex);
} catch (FileNotFoundException ex) { // unreachable
    handleMissingFile(ex);
}
```

Trình biên dịch biết mọi `FileNotFoundException` đã bị `catch (IOException)` phía trên bắt mất.

### Kiểu quan trọng hơn thông điệp

Không nên chọn cách xử lý bằng cách phân tích chuỗi thông điệp:

```java
catch (IOException ex) {
    if (ex.getMessage().contains("not found")) {
        ...
    }
}
```

Thông điệp dành cho chẩn đoán và có thể thay đổi. Nếu bên gọi cần chính sách xử lý khác nhau, **kiểu exception hoặc ngữ cảnh có cấu trúc** thường là hợp đồng tốt hơn.

## <a id="exception-chaining">Chuỗi nguyên nhân (Exception Chaining)</a>

Lan truyền có thể giữ nguyên exception. Khi một exception mới được tạo ra từ exception cũ, Java cho phép nối chúng bằng `cause` để không làm mất nguyên nhân.

Ví dụ:

```java
Order load(long orderId) {
    try {
        return jdbcLoad(orderId);
    } catch (SQLException ex) {
        throw new RuntimeException(
                "Cannot load order " + orderId,
                ex
        );
    }
}
```

Luồng:

```text
SQLException
→ exception gốc

RuntimeException
→ exception mới

cause
→ vẫn giữ SQLException để chẩn đoán
```

`RuntimeException` ở ví dụ này chỉ là lớp bọc để quan sát cơ chế. Điểm cần giữ là **chuỗi nguyên nhân (chaining)**: exception cũ phải được truyền vào làm `cause` của exception mới. Việc nên dùng kiểu lớp bọc nào và có nên chuyển đổi ở ranh giới trừu tượng hay không sẽ được học ở các chương về Custom Exception và ranh giới xử lý.

## <a id="lost-cause-pitfall">Không làm mất nguyên nhân (Cause)</a>

Cách viết cần tránh:

```java
catch (SQLException ex) {
    throw new RuntimeException("load failed");
}
```

Exception mới không biết `SQLException` cũ là nguyên nhân.

Hệ quả:

```text
log
→ chỉ thấy lớp bọc

stack trace gốc
→ mất khỏi chuỗi nguyên nhân

chẩn đoán
→ khó biết lỗi cơ sở dữ liệu nằm ở đâu
```

Cách đúng khi tạo exception mới từ exception cũ:

```java
catch (SQLException ex) {
    throw new RuntimeException("load failed", ex);
}
```

Phần này chỉ tập trung vào việc **không làm mất cause**. Quyết định ném lại exception cũ hay bọc bằng exception mới sẽ được xử lý ở chương về ranh giới ứng dụng.

### GHI NHỚ PHẦN CỐT LÕI

```text
throw
→ bắt đầu luồng xử lý bất thường

lan truyền (propagation)
→ lỗi đi lên ngăn xếp lời gọi

stack unwinding
→ các khung ngăn xếp được rời bỏ, công việc dọn dẹp liên quan vẫn phải chạy

catch
→ chặn lan truyền tại tầng có trách nhiệm

lớp bọc mới + cause
→ tạo exception mới nhưng vẫn giữ nguyên nhân gốc
```

Các phần còn lại của chương là lớp kiến thức nâng cao về checked exception: precise rethrow và quy tắc `throws` khi ghi đè. Người học có thể quay lại các phần này sau khi đã nắm chắc luồng lan truyền, cách chọn `catch` và chuỗi nguyên nhân.

## <a id="precise-rethrow">Ném lại chính xác (Precise Rethrow)</a>

Precise rethrow giải quyết một tình huống tinh tế: ta bắt bằng một kiểu rộng để làm một việc chung, nhưng trình biên dịch vẫn có thể suy luận các checked exception cụ thể thực sự đi vào `catch`.

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

Giả sử mã nguồn trong `try` chỉ có thể ném `IOException` hoặc `SQLException`. Nếu biến `ex` không bị gán lại, trình biên dịch có thể suy luận việc ném lại chỉ thuộc các kiểu checked đó.

Ta **không bắt buộc** phải đổi phần khai báo thành:

```java
throws Exception
```

### VÌ SAO?

Không có precise rethrow, việc bắt một kiểu cha (supertype) để dùng logic chung có thể làm hợp đồng bị rộng không cần thiết.

Mô hình cần nhớ:

```text
tham số `catch` được khai báo rộng
        ↓
trình biên dịch phân tích những kiểu checked thực sự có thể tới `catch`
        ↓
ex không bị thay thế bằng giá trị khác
        ↓
việc ném lại vẫn giữ được tập kiểu hẹp
```

Multi-catch:

```java
catch (IOException | SQLException ex) {
    throw ex;
}
```

cũng giữ tập kiểu rõ ràng, nhưng đó là **multi-catch**. Precise rethrow đáng chú ý nhất khi tham số được khai báo bằng một kiểu cha (supertype) rộng hơn như `Exception` mà trình biên dịch vẫn suy luận được tập checked exception cụ thể.

## <a id="override-throws-rules">Quy tắc `throws` khi ghi đè (override)</a>

Giả sử hợp đồng của lớp cha là:

```java
class Parent {
    void load() throws IOException {
    }
}
```

Phương thức ghi đè không được mở rộng hợp đồng checked exception:

```java
class Child extends Parent {
    @Override
    void load() throws Exception { // compile error
    }
}
```

### VÌ SAO?

Mã nguồn có thể giữ tham chiếu kiểu `Parent`:

```java
Parent value = new Child();
value.load();
```

Bên gọi được biên dịch dựa trên hợp đồng của `Parent`. Nếu `Child` được phép phát sinh một checked exception rộng hơn, bên gọi có thể gặp checked exception mà hợp đồng ban đầu không yêu cầu chuẩn bị.

Đây là một phần của nguyên tắc thay thế (substitutability):

```text
kiểu con có thể thu hẹp loại lỗi được khai báo
nhưng không được buộc bên gọi xử lý checked exception rộng hơn hợp đồng cha
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

`Child` cam kết một hợp đồng bằng hoặc dễ xử lý hơn đối với checked exception.

### GHI NHỚ

```text
throw
→ hành động khi chương trình chạy

throws
→ hợp đồng khai báo

ghi đè (override)
→ không được mở rộng hợp đồng checked exception

precise rethrow
→ `catch` rộng không nhất thiết làm `throws` bị rộng
```

Chương tiếp theo đi vào nơi quá trình lan truyền bị chặn: `try`, `catch` và `finally` phối hợp thế nào trên từng đường chạy?
