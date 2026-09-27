# StringBuilder

`StringBuilder` is not a “mutable String”. It is a **mutable buffer for constructing a String**, followed by `toString()` to produce the immutable result.

## <a id="builder-mutable-buffer">Mutable Buffer</a>

```java
StringBuilder builder = new StringBuilder();
builder.append("Hello");
builder.append(' ');
builder.append(name);
String result = builder.toString();
```

Unlike String operations, multiple `append` calls mutate the same builder's internal buffer.

That makes it a natural tool for incremental construction inside a controlled scope.

### WHY — what problem does it solve?

String immutability is excellent for sharing but less suitable for building one result through many mutation-like steps:

```text
repeated String concatenation
→ many intermediate immutable values

StringBuilder
→ one mutable construction object
→ append many times
→ toString when complete
```

### Fluent append

Many mutating methods return the builder itself:

```java
String result = new StringBuilder()
        .append("user=")
        .append(userId)
        .append(", active=")
        .append(active)
        .toString();
```

`append` has overloads for primitives, Strings, character sequences, and objects. The builder is a construction tool, not a mutable String value.

## <a id="builder-capacity">Length vs Capacity</a>

`length()` is the current character/code-unit count. `capacity()` is the current internal buffer capacity before growth is required.

Capacity is a performance concern rather than part of text semantics. Pre-sizing can reduce resizing when a large final size is reasonably predictable.

### Capacity is not a maximum length

```java
StringBuilder b = new StringBuilder(8);
b.append("this text is longer than eight");
```

The builder grows when needed. Initial capacity is a performance hint, not a maximum-length contract.

The exact growth strategy is an implementation detail. `ensureCapacity(n)` can help when a large lower bound is known, but it should be driven by a real workload or measurement.

### setLength deserves care

`setLength` can truncate or extend a builder. When extending it, the new positions contain the null character (`\u0000`), not spaces. It is not a “pad with spaces” API.

## <a id="builder-usage">Incremental String Construction</a>

Use a builder when text is assembled through loops, many branches, or repeated appends.

After `toString()`, the resulting String is immutable in the usual String sense; later builder mutations do not turn that String into mutable text.

### Snapshot semantics

```java
StringBuilder b = new StringBuilder("java");

String first = b.toString();
b.append("-core");
String second = b.toString();

System.out.println(first);  // java
System.out.println(second); // java-core
```

`first` does not change when the builder changes later.

### Common buffer operations

Besides `append`:

```java
builder.insert(...);
builder.delete(...);
builder.replace(...);
builder.reverse();
```

Use them when you are genuinely editing a **construction buffer**. If you already have a final String and only need a simple transformation, String APIs often express the intent more directly.

### Thread-safety boundary

`StringBuilder` is not synchronized and is not intended for one mutable instance to be modified concurrently without coordination.

A common pattern is:

```text
method/thread-local builder
→ build
→ toString
→ publish immutable String
```

The next chapter compares `StringBuilder` with synchronized `StringBuffer`.
