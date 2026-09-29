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

Compile-time constant concatenation may be folded into the same pooled literal:

```java
String a = "ja" + "va";
String b = "java";
```

Runtime concatenation does not carry the same identity guarantee.

### final does not automatically mean compile-time constant

A `final` variable participates in a constant expression only when it is actually a Java **constant variable**:

```java
final String prefix = "ja";
String a = prefix + "va";
String b = "java";

System.out.println(a == b); // true
```

But a value determined at runtime is different:

```java
final String prefix = args.length > 0 ? args[0] : "ja";
String a = prefix + "va";
String b = "java";

System.out.println(a.equals(b));
```

`final` prevents reassignment; it does not convert every runtime expression into a compile-time constant.

### Practical rule

```text
same object?
→ ==

same text?
→ equals

need a canonical pooled reference deliberately?
→ intern(), with explicit trade-offs
```

Application logic should never depend on pool identity for text equality.

The next chapter focuses directly on String comparison rules.
