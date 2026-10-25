# String Pool

Because Strings are immutable, the runtime can safely share selected equal values instead of always allocating separate objects. That is the core idea behind the String pool.

## <a id="string-pool-model">What Is the String Pool?</a>

The pool provides canonical references for selected Strings, especially literals and explicitly interned values.

### WHY — why have a pool?

Strings appear everywhere in source code: class names, keys, messages, paths, tokens, and constants. If every identical literal required a separate object, the runtime could retain many identities with no additional semantic value.

String immutability makes canonical sharing safe:

```text
equal immutable String values
        ↓
safe shared canonical identity
        ↓
String pool
```

```java
String a = "java";
String b = "java";
```

Because equal String literals are interned, these two literals refer to the same canonical pooled String object.

For String literals, sharing is not merely an accidental optimization: literals and constant String expressions participate in interning semantics. Where a particular JVM stores its pool is an implementation detail; do not tie the concept to an old PermGen-era memory story.

Pooling is an **identity optimization/canonicalization mechanism**. It does not change the rule that text content should be compared with `equals`.

The pool is also not a public `Map` that application code can iterate, remove from, or mutate. Treat it as a mental model for canonical identity.

## <a id="literal-vs-new">Literal vs new String</a>

```java
String a = "java";
String b = new String("java");
```

`a` refers to the pooled literal, while `new String(...)` requests a distinct object.

Therefore `a == b` is false while `a.equals(b)` is true.

Avoid `new String("...")` when a literal already expresses the intended value.

`new` does not make a String mutable:

```java
String x = new String("java");
x.toUpperCase();

System.out.println(x); // java
```

The constructor changes object creation, not the immutability contract.

## <a id="pool-identity">Pool Identity and Constant Expressions</a>

A compile-time String constant expression produces an interned constant String, so it can share the canonical reference of the corresponding literal:

```java
String a = "ja" + "va";
String b = "java";
```

Runtime concatenation does not carry the same identity guarantee.

The exact rules for **constant expressions**, `final` variables, and compile-time concatenation belong to the Concatenation chapter. The pool-level mental model is only that identity depends on how the value is produced; content comparison still uses `equals`.

### Practical rule

```text
same object?
→ ==

same text?
→ equals

need a canonical pooled reference deliberately?
→ intern(), with explicit trade-offs
```

Application logic should never depend on pool identity for text equality. The pool is a canonical-reuse mechanism; for literals and constant String expressions, interning is part of Java's specified semantics rather than merely an optional runtime optimization.

The next chapter focuses directly on String comparison rules.
