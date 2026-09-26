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

## <a id="builder-capacity">Length vs Capacity</a>

`length()` is the current character/code-unit count. `capacity()` is the current internal buffer capacity before growth is required.

Capacity is a performance concern rather than part of text semantics. Pre-sizing can reduce resizing when a large final size is reasonably predictable.

## <a id="builder-usage">Incremental String Construction</a>

Use a builder when text is assembled through loops, many branches, or repeated appends.

After `toString()`, the resulting String is immutable in the usual String sense; later builder mutations do not turn that String into mutable text.

The next chapter compares `StringBuilder` with synchronized `StringBuffer`.
