# try, catch và finally

## <a id="try-catch-flow">Control flow của try/catch</a>
Code trong `try` chạy tới khi complete bình thường hoặc throw. Nếu throwable match catch clause, control chuyển tới handler compatible đầu tiên; nếu không nó tiếp tục propagate. Sau handling, execution tiếp tục sau toàn construct trừ khi handler return/throw.

## <a id="finally-semantics">Semantics của finally</a>
`finally` bình thường chạy dù `try` complete, return hay throw và dù catch có handle failure hay không. Nó dành cho cleanup cần xảy ra bất kể outcome. Process termination/fatal VM condition có thể ngăn finally, nên đây không phải external durability guarantee.

## <a id="return-finally">Tương tác return/throw với finally</a>
`finally` chạy sau khi return value đã được xác định nhưng trước khi control thật sự rời method. Nếu chính `finally` return hoặc throw, nó có thể thay pending return/exception và che outcome gốc.

```java
try {
    return 1;
} finally {
    return 2; // che return ban đầu; nên tránh
}
```

Không dùng `return` hoặc normal-flow `throw` trong `finally` chỉ để rút ngắn control flow.

## <a id="multi-catch">Multi-catch và alternative</a>
Multi-catch (`catch (IOException | SQLException e)`) phù hợp khi các exception type không liên quan cần cùng handling. Các alternative trong một multi-catch không được có quan hệ subclass vì broader type đã cover narrower type. Chỉ gom khi recovery/translation semantics thật sự giống nhau.
