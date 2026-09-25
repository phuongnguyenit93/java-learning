# String Intern

## <a id="intern-semantics">Semantics của String.intern</a>
`intern()` trả canonical pooled String equal với receiver. Nếu pool đã có equal canonical string thì trả reference đó; nếu chưa, receiver value sẽ được represented canonical theo JVM behavior.

## <a id="intern-identity">Canonical pool reference</a>
Sau intern các string equal, identity có thể share nên `a.intern() == b.intern()` có thể true khi content bằng nhau. Đây là canonicalization mechanism, không phải replacement cho `equals` trong ordinary text logic.

## <a id="intern-tradeoffs">Trade-off interning và memory</a>
Interning có thể giảm duplicate storage hoặc cho canonical identity với vocabulary bounded, nhưng arbitrary/high-cardinality user data có thể làm pool tăng và thêm lookup overhead. Modern JVM không còn đúng các permanent-generation myth cũ, nhưng unbounded interning vẫn là ownership/memory decision cần measure.
