# Propagation

Khi một method không xử lý exception, failure không biến mất. Java **gỡ dần call stack (stack unwinding)** để tìm một handler phù hợp ở caller phía trên.

Đây là cầu nối giữa `throw` và `try/catch`: `throw` tạo ra sự kết thúc bất thường tại một điểm; propagation quyết định failure đó đi đâu tiếp theo.

## <a id="exception-propagation">Exception Propagation</a>

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

Không method nào catch exception, nên flow là:

```text
repository()
→ dừng tại throw
→ rời repository frame

service()
→ không tiếp tục sau repository()
→ rời service frame

controller()
→ exception tiếp tục truyền lên caller của controller()
```

Quá trình rời từng frame như vậy là **stack unwinding**.

### Điều gì xảy ra trong lúc unwind?

“Rời frame” không có nghĩa Java bỏ qua mọi cleanup. Trước khi frame thực sự kết thúc:

- `finally` tương ứng vẫn có thể chạy;
- resource của `try-with-resources` được close theo contract;
- một handler phù hợp trong chính frame đó vẫn có thể chặn propagation.

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

Nếu exception thoát khỏi entry point của thread, nó đi tới cơ chế xử lý uncaught exception của thread. Thread đó kết thúc vì exceptional completion.

Điều này không đồng nghĩa mọi exception thoát thread đều lập tức “kill toàn bộ JVM”; hành vi tiến trình còn phụ thuộc các thread khác và runtime context. Với mental model của module, điều quan trọng là:

```text
không có handler
→ failure tiếp tục lên tới boundary cuối của thread
```

### VÌ SAO PROPAGATION HỮU ÍCH?

Repository không cần biết:

- UI sẽ hiển thị thông báo gì;
- request HTTP sẽ trả status nào;
- batch job có retry không;
- tầng trên có translate exception không.

Nó chỉ báo failure. Policy được đặt ở tầng có đủ context.

## <a id="catch-selection">Chọn catch theo Type</a>

Khi Java gặp một `try` có nhiều `catch`, nó xét handler theo thứ tự source và chọn `catch` đầu tiên mà parameter type có thể nhận exception đang được ném.

```java
try {
    load();
} catch (FileNotFoundException ex) {
    handleMissingFile(ex);
} catch (IOException ex) {
    handleOtherIo(ex);
}
```

`FileNotFoundException` là subtype của `IOException`, nên branch cụ thể phải đứng trước branch rộng.

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

Compiler biết mọi `FileNotFoundException` đã bị `catch (IOException)` phía trên bắt mất.

### Type quan trọng hơn message

Không nên chọn handler bằng cách parse message:

```java
catch (IOException ex) {
    if (ex.getMessage().contains("not found")) {
        ...
    }
}
```

Message dành cho chẩn đoán và có thể thay đổi. Nếu caller cần policy khác nhau, **type hoặc structured context** thường là contract tốt hơn.

## <a id="exception-chaining">Exception Chaining</a>

Propagation cho phép giữ nguyên exception. Nhưng tại abstraction boundary, type thấp hơn có thể làm caller phụ thuộc vào implementation detail.

Ví dụ repository dùng JDBC:

```java
Order load(long orderId) {
    try {
        return jdbcLoad(orderId);
    } catch (SQLException ex) {
        throw new OrderRepositoryException(
                "Cannot load order " + orderId,
                ex
        );
    }
}
```

Flow:

```text
SQLException
→ chi tiết của storage/JDBC

OrderRepositoryException
→ vocabulary của repository/application

cause
→ vẫn giữ SQLException để debug
```

Đây là hai hành động cùng lúc:

```text
translation
→ đổi abstraction vocabulary

chaining
→ giữ causal chain
```

### Khi nào nên wrap?

Wrap khi tầng hiện tại thực sự tạo ra một abstraction boundary có ý nghĩa.

Không cần wrap vô điều kiện kiểu:

```text
IOException
→ MyIOException
→ ServiceIOException
→ ControllerIOException
```

nếu các lớp mới không thêm contract, context hoặc handling category nào. Wrap quá nhiều làm causal chain dài mà không tăng giá trị.

## <a id="lost-cause-pitfall">Đừng làm mất Cause</a>

Anti-pattern:

```java
catch (SQLException ex) {
    throw new OrderRepositoryException("load failed");
}
```

Exception mới không biết `SQLException` cũ là nguyên nhân.

Hệ quả:

```text
log
→ chỉ thấy wrapper

root stack trace
→ mất khỏi causal chain

debug
→ khó biết database failure nằm ở đâu
```

Cách đúng khi cần translation:

```java
catch (SQLException ex) {
    throw new OrderRepositoryException("load failed", ex);
}
```

### Rethrow nguyên object hay wrap?

Nếu abstraction không đổi:

```java
catch (IOException ex) {
    audit(ex);
    throw ex;
}
```

có thể hợp lý.

Nếu abstraction đổi:

```java
catch (IOException ex) {
    throw new OrderLoadException("Cannot load order file", ex);
}
```

có thể rõ hơn.

Không nên tạo wrapper chỉ vì “đã catch thì phải throw exception mới”.

### GHI NHỚ

```text
throw
→ bắt đầu exceptional flow

propagation
→ failure đi lên call stack

stack unwinding
→ frame được rời bỏ, cleanup liên quan vẫn phải chạy

catch
→ chặn propagation tại tầng có trách nhiệm

translation + cause
→ đổi vocabulary nhưng không mất root failure
```

Chương tiếp theo đi vào nơi propagation bị chặn: `try`, `catch` và `finally` phối hợp thế nào trên từng đường chạy?