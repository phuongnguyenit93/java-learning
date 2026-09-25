# String Pool

## <a id="string-pool-model">Mental model String pool</a>
JVM giữ pool canonical String instance cho literal và string được intern explicit trong runtime/class-loader context. Pooling khả thi vì String immutable: share một canonical object không cho caller này sửa text của caller khác.

## <a id="literal-vs-new">Literal và new String</a>
Literal như `"abc"` refer tới pooled canonical string. `new String("abc")` tạo String object khác với content equal, vì vậy identity khác dù `equals` true.

## <a id="pool-identity">Pool identity và compile-time constant</a>
Compile-time constant string expression có thể được fold và share pooled identity, còn runtime concatenation thường tạo result trước khi intern explicit. Không dùng `==` để test text content; pool identity là optimization detail chứ không phải value-equality contract.
