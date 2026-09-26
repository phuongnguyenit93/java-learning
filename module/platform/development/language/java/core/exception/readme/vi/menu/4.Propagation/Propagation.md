# Propagation

Khi một method không xử lý exception, lỗi không biến mất. Java **gỡ dần call stack (stack unwinding)** để tìm một `catch` phù hợp ở bên gọi phía trên.

## <a id="exception-propagation">Exception Propagation</a>

Ví dụ:

```text
controller()
→ service()
   → repository()
      → throw IOException
```

Nếu `repository()` không catch, exception quay về `service()`. Nếu `service()` cũng không catch, nó tiếp tục lên `controller()` và cứ thế cho tới khi tìm được nơi xử lý phù hợp hoặc thoát khỏi thread.

Trong quá trình này, các stack frame trung gian được rời bỏ. Đây là **stack unwinding**.

### MÔ HÌNH TƯ DUY

Propagation cho phép tầng thấp báo lỗi mà không cần biết tầng cao cuối cùng sẽ khôi phục, chuyển đổi lỗi hay kết thúc luồng xử lý.

## <a id="catch-selection">Chọn catch theo Type</a>

Java chọn `catch` đầu tiên có type tương thích với exception đang propagate.

Vì vậy catch cụ thể phải đứng trước catch rộng hơn:

```java
try {
    ...
} catch (FileNotFoundException ex) {
    ...
} catch (IOException ex) {
    ...
}
```

Nếu đảo thứ tự, catch rộng có thể làm catch cụ thể unreachable và compiler từ chối.

## <a id="exception-chaining">Exception Chaining</a>

Tại ranh giới abstraction, tầng trên có thể muốn đổi cách diễn đạt lỗi nhưng vẫn giữ nguyên nguyên nhân gốc:

```java
try {
    repository.load();
} catch (SQLException ex) {
    throw new OrderRepositoryException("Cannot load order", ex);
}
```

Đây là exception translation + chaining:

```text
nguyên nhân ở tầng thấp
→ được giữ làm cause

exception ở tầng cao
→ diễn đạt lỗi bằng ngôn ngữ phù hợp tầng hiện tại
```

## <a id="lost-cause-pitfall">Đừng làm mất Cause</a>

Anti-pattern:

```java
catch (SQLException ex) {
    throw new OrderRepositoryException("load failed");
}
```

Nếu bỏ `ex`, chuỗi chẩn đoán bị cắt. Log chỉ thấy lỗi mới mà mất stack trace và ngữ cảnh của lỗi gốc.

Chương tiếp theo tập trung vào việc **xử lý exception và đảm bảo dọn dẹp** bằng `try`, `catch`, `finally`.
