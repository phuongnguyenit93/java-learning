# String.intern

The String pool can provide canonical identity for literals and selected Strings. `String.intern()` explicitly asks for the **canonical pooled reference** corresponding to the same text content.

## <a id="intern-semantics">What Does String.intern Do?</a>

```java
String canonical = value.intern();
```

The runtime returns the pool reference associated with the same String content.

This does not mutate `value` and does not change String equality semantics. Interning is about **identity/canonicalization**, not a different definition of text equality.

## <a id="intern-identity">Canonical Pool Reference</a>

```java
String a = new String("java");
String b = a.intern();
String c = "java";

b == c // true in the corresponding runtime context
```

`b` uses the canonical pooled reference for `"java"`.

Application logic should still use `equals` when the question is content equality.

## <a id="intern-tradeoffs">Interning Trade-offs</a>

Interning can reduce duplicate identities for a highly repetitive String set, but it is not a default optimization for all applications.

Consider lookup/canonicalization cost, pool retention, high-cardinality input, and unnecessary identity coupling.

Intern when the workload and measurements justify it, or when canonical identity is genuinely part of the design.

The next chapter crosses a more important representation boundary: how does Java text become external bytes?
