# String Interning

## <a id="intern-semantics">String.intern semantics</a>
`intern()` returns a canonical pooled String equal to the receiver. If an equal canonical string is already in the pool, that reference is returned; otherwise the receiver's value becomes represented canonically according to JVM behavior.

## <a id="intern-identity">Canonical pool reference</a>
After interning equal strings, identity can be shared, so `a.intern() == b.intern()` can be true when contents are equal. This is a canonicalization mechanism, not a recommended replacement for `equals` in ordinary text logic.

## <a id="intern-tradeoffs">Interning trade-offs and memory considerations</a>
Interning can reduce duplicate storage or make canonical identity useful for carefully bounded vocabularies, but arbitrary/high-cardinality user data can grow the pool and add lookup overhead. Modern collectors/runtime implementations mitigate old permanent-generation myths, yet unbounded interning is still an ownership/memory decision that should be measured.
