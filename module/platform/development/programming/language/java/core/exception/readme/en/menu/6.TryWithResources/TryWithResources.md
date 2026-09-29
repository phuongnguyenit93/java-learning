# Try-with-resources

Files, streams, sockets, JDBC objects, and other resources outside ordinary heap memory need to be **released at a defined time**. The Garbage Collector manages Java object memory; it is not the contract for timely release of file descriptors, sockets, or database handles.

Try-with-resources (TWR) makes resource ownership and cleanup explicit in the language.

## <a id="autocloseable">AutoCloseable</a>

A resource used in TWR must have a type that implements `AutoCloseable`:

```java
public interface AutoCloseable {
    void close() throws Exception;
}
```

Example:

```java
try (InputStream in = Files.newInputStream(path)) {
    return in.read();
}
```

Basic flow:

```text
create resource
        ↓
execute body
        ↓
leave by success / return / throw
        ↓
Java invokes close()
        ↓
control continues outside
```

### WHY NOT WAIT FOR THE GARBAGE COLLECTOR?

A stream object may become unreachable while the underlying OS resource still needs deterministic release.

Mental model:

```text
memory lifetime
≠
external resource lifetime
```

TWR solves **lifecycle/ownership**, not GC optimization.

### Where do catch and finally fit?

```java
try (InputStream in = Files.newInputStream(path)) {
    use(in);
} catch (IOException ex) {
    handle(ex);
} finally {
    afterOperation();
}
```

Resources are closed when leaving the resource `try` before the surrounding `catch`/`finally` finishes handling the outcome according to TWR semantics.

That lets handlers observe a failure after resource cleanup has already participated in the exit sequence.

## <a id="resource-close-order">Reverse Resource Close Order</a>

Resources are initialized left to right:

```java
try (A a = openA();
     B b = openB();
     C c = openC()) {
    use(a, b, c);
}
```

Initialization:

```text
A → B → C
```

Closing is reversed:

```text
C → B → A
```

### WHY REVERSE THE ORDER?

Later resources often depend on earlier resources.

For example:

```java
try (InputStream raw = Files.newInputStream(path);
     BufferedInputStream buffered = new BufferedInputStream(raw)) {
    ...
}
```

`buffered` wraps `raw`, so the wrapper should be closed first.

### What if initialization fails in the middle?

Important edge case:

```java
try (A a = openA();
     B b = openB();   // throws here
     C c = openC()) {
    ...
}
```

Result:

```text
A initialized successfully
B initialization fails
C is never created
        ↓
A is still closed
```

TWR cleans resources that were successfully initialized before the failure point.

Doing this correctly with several nested manual `try/finally` blocks is much harder.

## <a id="effective-final-resource">Effective-final Resources</a>

Since Java 9, an existing local variable can appear directly in the resource specification if it is `final` or effectively final:

```java
InputStream in = Files.newInputStream(path);

try (in) {
    consume(in);
}
```

Effectively final means the variable is not reassigned after initialization.

This is not effectively final:

```java
InputStream in = Files.newInputStream(path);
in = anotherStream;

try (in) {
    ...
}
```

### Ownership still matters

The syntax allowing an existing variable does not mean every method receiving an `AutoCloseable` should close it.

Ask:

```text
Who created the resource?
Who owns its lifecycle?
Who is responsible for closing it?
```

If a method only borrows a caller-owned resource, closing it may violate the caller's contract.

## <a id="twr-vs-finally">Try-with-resources vs Manual finally</a>

Manual cleanup:

```java
InputStream in = Files.newInputStream(path);
try {
    return in.read();
} finally {
    in.close();
}
```

looks simple until:

```text
body throws A
and
close throws B
```

With manual `finally`, B can hide A unless the code carefully preserves both.

TWR defines standard semantics that keep the primary failure and attach cleanup failures as suppressed exceptions.

### Comparison

```text
manual finally
→ manually manage nulls, close order, multiple resources, multiple failures

try-with-resources
→ ownership is visible in syntax
→ close order is defined
→ partial initialization is cleaned up
→ primary/suppressed failure semantics are preserved
```

TWR does not replace every use of `finally`. `finally` remains useful for exit work that is not naturally an `AutoCloseable` lifecycle. But for owned resources, TWR is usually the better default.

### REMEMBER

```text
resource specification
→ explicit ownership/lifecycle

initialization
→ left to right

closing
→ right to left

mid-initialization failure
→ earlier successful resources are still closed

body + close both fail
→ suppressed-exception semantics are required
```

The next chapter examines that final case: when the operation and cleanup both fail, how does Java preserve both failures?