# throw và throws

`throw` và `throws` cùng liên quan exception nhưng làm hai việc rất khác nhau:

```text
throw
→ tại runtime, ném một Throwable cụ thể

throws
→ trong khai báo method, công bố một checked exception có thể thoát ra
```

## <a id="throw-statement">throw</a>

`throw` là một câu lệnh làm luồng điều khiển rời khỏi đường chạy hiện tại:

```java
if (amount < 0) {
    throw new IllegalArgumentException("amount must be >= 0");
}
```

Biểu thức sau `throw` phải tạo ra một đối tượng thuộc hệ phân cấp `Throwable`.

Sau `throw`, luồng thực thi không tiếp tục sang câu lệnh kế tiếp trong block hiện tại. JVM tìm một `catch` có kiểu phù hợp; nếu không có, exception tiếp tục được truyền lên call stack.

## <a id="throws-clause">throws</a>

`throws` nằm trong method declaration:

```java
String load(Path path) throws IOException {
    ...
}
```

Nó không tự ném exception. Nó mô tả rằng checked exception đó **có thể thoát khỏi method**, buộc bên gọi phải nhận biết hợp đồng này.

Một method có thể khai báo nhiều exception nếu hợp đồng thật sự cần, nhưng danh sách quá rộng thường là dấu hiệu ranh giới abstraction chưa rõ.

## <a id="precise-rethrow">Precise Rethrow</a>

Java có thể giữ kiểu của checked exception hẹp hơn trong một số trường hợp rethrow.

```java
try {
    run();
} catch (IOException | SQLException ex) {
    throw ex;
}
```

Compiler có thể suy luận `ex` chỉ thuộc những kiểu đã được catch và rethrow chính xác hơn, thay vì bắt method khai báo một supertype quá rộng.

Điều này giúp giữ hợp đồng cụ thể hơn mà không cần lặp lại nhiều catch block.

## <a id="override-throws-rules">Quy tắc throws khi Override</a>

Method override **không được mở rộng checked-exception hợp đồng** so với method cha.

Nếu parent chỉ cho phép:

```java
void run() throws IOException;
```

thì child không thể đổi thành:

```java
void run() throws Exception;
```

vì bên gọi dùng parent hợp đồng không được chuẩn bị cho checked lỗi rộng hơn đó.

## <a id="checked-exception-narrowing">Thu hẹp Checked Exception</a>

Method override có thể:

- giữ checked exception như parent;
- declare subtype hẹp hơn;
- bỏ checked exception hoàn toàn.

Unchecked exception không chịu cùng giới hạn compile-time vì nó không nằm trong checked hợp đồng.

Chương tiếp theo xem điều gì xảy ra sau khi một exception được throw nhưng không được xử lý ngay tại method hiện tại.
