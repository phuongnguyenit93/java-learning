# Exception Propagation

## <a id="exception-propagation">Stack unwinding và propagation</a>
Khi method throw mà không handle, frame của nó exit và exception propagate lên caller. Quá trình tiếp tục tới matching handler hoặc thread terminate. `finally`/resource cleanup chạy theo language rule trong unwinding.

## <a id="catch-selection">Chọn catch theo type</a>
Catch clause được xét từ trên xuống; type đầu tiên assignment-compatible sẽ handle throwable. Catch specific phải đứng trước catch broader nếu không clause sau trở thành unreachable.

## <a id="exception-chaining">Wrap và preserve cause</a>
Translate low-level exception khi qua abstraction boundary nếu caller cần high-level meaning hơn. Preserve original cause:

```java
catch (SQLException e) {
    throw new OrderRepositoryException("Cannot load order " + id, e);
}
```

Cách này thêm domain context mà không phá diagnostic.

## <a id="lost-cause-pitfall">Anti-pattern làm mất cause</a>
Tạo exception mới chỉ với `e.getMessage()` làm mất original type, stack, suppressed exception và causal chain. Cũng tránh log rồi rethrow ở mọi layer gây duplicate trace. Preserve cause và để responsible boundary quyết định reporting.
