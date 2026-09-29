# String.intern

The String pool can provide canonical identity for literals and selected Strings. `String.intern()` explicitly asks for the **canonical pooled reference** corresponding to the same text content.

## <a id="intern-semantics">What Does String.intern Do?</a>

```java
String canonical = value.intern();
```

The runtime returns the pool reference associated with the same String content.

Contract mental model:

```text
value.intern()
        │
        ├── pool already has String equals(value)
        │      → return existing canonical reference
        │
        └── not present
               → value becomes the canonical representative
               → return canonical reference
```

This does not mutate `value` and does not change String equality semantics. Interning is about **identity/canonicalization**, not a different definition of text equality.

### WHY use intern?

Interning lets an application request canonical identity for equal String values. It can help selected workloads with highly repeated values and controlled cardinality.

If the only question is “do these strings have the same content?”, `equals` already answers it; interning first is unnecessary.

## <a id="intern-identity">Canonical Pool Reference</a>

```java
String a = new String("java");
String b = a.intern();
String c = "java";

b == c // true
```

`b` uses the canonical pooled reference for `"java"`.

Application logic should still use `equals` when the question is content equality.

Interning connects directly to literals:

```java
String runtime = new String("java");
String canonical = runtime.intern();
String literal = "java";

canonical == literal // true
```

This is an appropriate identity experiment because canonicalization itself is the concept being observed.

## <a id="intern-tradeoffs">Interning Trade-offs</a>

Interning can reduce duplicate identities for a highly repetitive String set, but it is not a default optimization for all applications.

Consider lookup/canonicalization cost, pool retention, high-cardinality input, and unnecessary identity coupling.

### High-cardinality and untrusted input

If values are almost always unique:

```text
user-000001
user-000002
user-000003
...
```

canonicalization may provide little deduplication while still adding lookup and retention pressure.

Avoid interning high-cardinality user-controlled data merely because it sounds memory-efficient.

### Avoid old JVM folklore

Statements such as “interned strings always live in PermGen” are implementation/version-specific history, not a Java language contract.

Reason in terms of:

```text
canonical identity
lookup cost
retention/memory profile
workload cardinality
```

Intern when the workload and measurements justify it, or when canonical identity is genuinely part of the design.

The next chapter crosses a more important representation boundary: how does Java text become external bytes?
