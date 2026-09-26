# String Pool

Because Strings are immutable, the runtime can safely share selected equal values instead of always allocating separate objects. That is the core idea behind the String pool.

## <a id="string-pool-model">What Is the String Pool?</a>

The pool provides canonical references for selected Strings, especially literals and explicitly interned values.

```java
String a = "java";
String b = "java";
```

In the same runtime context, these literals normally share the pooled object.

Pooling is an **identity optimization/canonicalization mechanism**. It does not change the rule that text content should be compared with `equals`.

## <a id="literal-vs-new">Literal vs new String</a>

```java
String a = "java";
String b = new String("java");
```

`a` refers to the pooled literal, while `new String(...)` requests a distinct object.

Therefore `a == b` is normally false while `a.equals(b)` is true.

Avoid `new String("...")` when a literal already expresses the intended value.

## <a id="pool-identity">Pool Identity and Constant Expressions</a>

Compile-time constant concatenation may be folded into the same pooled literal:

```java
String a = "ja" + "va";
String b = "java";
```

Runtime concatenation does not carry the same identity guarantee.

Application logic should never depend on pool identity for text equality.

The next chapter focuses directly on String comparison rules.
