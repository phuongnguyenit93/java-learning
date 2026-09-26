# Try-with-resources

Files, streams, sockets, and similar resources need deterministic cleanup rather than waiting for garbage collection. Try-with-resources makes ownership and cleanup explicit in the language.

## <a id="autocloseable">AutoCloseable</a>

A resource used in try-with-resources implements `AutoCloseable`:

```java
try (InputStream in = Files.newInputStream(path)) {
    return in.read();
}
```

When the block exits, Java invokes `close()` automatically according to the try-with-resources rules.

## <a id="resource-close-order">Reverse Close Order</a>

For resources declared as:

```java
try (A a = ...; B b = ...; C c = ...) {
    ...
}
```

the close order is:

```text
C → B → A
```

This mirrors stack-like ownership: later resources often depend on earlier ones and should be released first.

## <a id="effective-final-resource">Effective-final Resources</a>

Since Java 9, an existing local variable may be used directly if it is final or effectively final:

```java
InputStream in = Files.newInputStream(path);
try (in) {
    ...
}
```

## <a id="twr-vs-finally">Try-with-resources vs Manual finally</a>

Try-with-resources is usually safer because it reduces boilerplate, defines close order, makes cleanup harder to forget, and preserves the primary failure when `close()` also fails.

That last point leads directly to suppressed exceptions.
