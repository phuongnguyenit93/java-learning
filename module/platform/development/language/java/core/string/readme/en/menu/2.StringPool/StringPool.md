# String Pool

## <a id="string-pool-model">String pool mental model</a>
The JVM maintains a pool of canonical string instances for literals and strings explicitly interned in a class-loader/runtime context. Pooling is possible because String is immutable: sharing one canonical object cannot let one caller change another caller's text.

## <a id="literal-vs-new">Literal vs new String</a>
A literal such as `"abc"` refers to the pooled canonical string for that literal. `new String("abc")` creates a distinct String object initialized with equal content, so identity differs even though `equals` is true.

## <a id="pool-identity">Pool identity and compile-time constants</a>
Compile-time constant string expressions can be folded and share a pooled identity, while runtime concatenation generally produces a result object before any explicit interning. Never use `==` to test text content; pool identity is an implementation/language optimization detail, not a value-equality contract.
