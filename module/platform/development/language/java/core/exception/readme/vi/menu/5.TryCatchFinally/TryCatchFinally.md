# try, catch và finally

Propagation cho phép lỗi đi lên call stack. `try/catch/finally` cho phép một tầng quyết định: **xử lý gì, chuyển đổi lỗi gì và cần dọn dẹp gì dù thành công hay thất bại**.

## <a id="try-catch-flow">Luồng try/catch</a>

`try` bao quanh đoạn mã có thể phát sinh lỗi. Nếu exception xuất hiện, phần còn lại của `try` bị bỏ qua và JVM tìm `catch` có kiểu phù hợp.

```java
try {
    load();
    process(); // không chạy nếu load() throw
} catch (IOException ex) {
    recover(ex);
}
```

Catch nên tồn tại khi tầng hiện tại thực sự có trách nhiệm xử lý, chuyển đổi exception hoặc bổ sung ngữ cảnh. Catch chỉ để “cho hết exception” rồi không làm gì thường làm hệ thống khó debug hơn.

## <a id="finally-semantics">finally</a>

`finally` thường chạy khi rời khỏi `try/catch`, bất kể đường đi là:

- chạy thành công;
- `return`;
- `throw`;
- exception được catch hoặc tiếp tục truyền lên trên.

Nó phù hợp cho việc dọn dẹp thủ công, nhưng với tài nguyên triển khai `AutoCloseable` nên ưu tiên `try-with-resources` khi có thể.

## <a id="return-finally">return/throw bên trong finally</a>

Một `return` hoặc `throw` trong `finally` có thể **ghi đè kết quả hoặc exception đang trên đường thoát**.

Ví dụ nguy hiểm:

```java
try {
    throw new IllegalStateException("original");
} finally {
    return;
}
```

lỗi gốc có thể bị che mất.

Vì vậy tránh `return` trong `finally`; cũng cần rất thận trọng nếu `finally` có thể throw exception mới.

## <a id="multi-catch">Multi-catch</a>

Nếu nhiều exception cần cùng một cách xử lý, Java cho phép:

```java
catch (IOException | SQLException ex) {
    handle(ex);
}
```

Multi-catch giảm mã lặp nhưng không nên gom các lỗi có ngữ nghĩa khác nhau chỉ vì cách xử lý hiện tại đang giống nhau.

Chương tiếp theo thay việc dọn dẹp thủ công bằng một mô hình sở hữu tài nguyên rõ ràng hơn: `try-with-resources`.
