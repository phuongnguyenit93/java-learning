# Try-with-Resources

## <a id="autocloseable">Contract AutoCloseable</a>
Resource dùng trong try-with-resources phải implement `AutoCloseable` (hoặc `Closeable`). Construct sẽ chịu trách nhiệm close resource khai báo khi try scope kết thúc. `close()` có thể throw nên cleanup cũng tham gia exception handling.

## <a id="resource-close-order">Resource close theo thứ tự ngược</a>
Resource được close ngược declaration order, giống nested acquisition.

```java
try (A a = openA(); B b = openB()) {
    use(a, b);
} // b.close(), rồi a.close()
```

Điều này quan trọng khi resource sau phụ thuộc resource trước.

## <a id="effective-final-resource">Dùng effective-final resource từ Java 9</a>
Final/effectively-final variable khai báo trước `try` có thể được reference trực tiếp trong resource specification ở Java hiện đại. Variable không được reassign vì construct cần object ổn định để close.

## <a id="twr-vs-finally">Try-with-resources và manual finally</a>
Try-with-resources nên được ưu tiên cho `AutoCloseable` vì nó generate reverse-order cleanup đáng tin cậy và giữ close failure dưới dạng suppressed exception khi body đã fail. Manual `finally` dễ sai và dễ che primary failure.
