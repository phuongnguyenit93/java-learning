# String Immutability

## <a id="string-immutability">Vì sao String immutable</a>
Character sequence của một `String` object không đổi sau construction. Operation như `substring`, `replace`, `toUpperCase` hay concatenation tạo string value khác khi content thay đổi. Điều này giúp sharing an toàn và String làm hash key ổn định.

## <a id="immutability-consequences">Hệ quả về sharing, hashing và thread-safety</a>
Vì content không mutate, String có thể share giữa caller/thread mà không cần synchronization cho chính state của nó, cached hash vẫn valid và literal có thể pool an toàn. Immutability không làm surrounding mutable object thread-safe; nó chỉ ổn định String value.

## <a id="string-operation-new-value">String operation trả value mới</a>
Bỏ qua return value nghĩa là bỏ qua transformation.

```java
String s = " java ";
s.trim();       // s không đổi
s = s.trim();   // s refer tới "java"
```

Variable vẫn reassign được dù từng String object immutable.
