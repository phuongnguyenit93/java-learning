# Resource Safety with try-with-resources

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

Resources are closed when leaving the resource `try` **before** the `catch` and `finally` clauses attached to that try-with-resources statement begin handling the outcome.

Therefore, when a `catch` receives the exception, cleanup of resources owned by the try-with-resources statement has already run.

### What if a resource is null?

A try-with-resources resource may evaluate to `null`. This is legal:

```java
void useNullableResource() throws Exception {
    try (AutoCloseable resource = null) {
        // ...
    }
}
```

When leaving the `try`, Java invokes `close()` only when the resource is non-null. This is part of TWR semantics, not a reason to design around null resources; normal code should still keep ownership and resource state explicit.

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

`buffered` wraps `raw`, so the wrapper should be closed first. Many wrappers also close their wrapped resource from `close()`, however, so whether both resources belong in the same try-with-resources declaration depends on actual ownership; do not create accidental double-close responsibility.

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

TWR cleans resources that were successfully initialized before the failure point. If initializing B throws and `A.close()` also throws, B's initialization failure remains primary and the close failure from A is suppressed.

A further nuance: if `openB()` internally acquires something and then fails **before returning the B object**, TWR never obtains ownership of B and therefore cannot call `B.close()`. The factory/constructor creating B is responsible for cleaning up any partial acquisition before it throws.

Doing this correctly with several nested manual `try/finally` blocks is much harder.

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

The next part examines that final case: when the operation and cleanup both fail, how does Java preserve both failures?

Cleanup can fail too. Resource management therefore has a difficult case:

```text
the main operation already failed
        +
cleanup also fails
        ↓
do not lose either failure
```

Try-with-resources solves this with **primary** and **suppressed** exceptions.

## <a id="primary-vs-suppressed">Primary vs Suppressed</a>

Suppose the body throws A:

```java
try (DemoResource resource = new DemoResource()) {
    throw new IllegalArgumentException("body failed");
}
```

and `resource.close()` also throws B.

TWR preserves:

```text
A
→ primary exception

B
→ suppressed exception attached to A
```

### WHY KEEP A AS PRIMARY?

The operation already failed because of A. If cleanup failure B replaced it, diagnostics would report:

```text
"close failed"
```

while losing the failure that explains **why the main operation failed**.

Suppression preserves the full picture:

```text
primary
→ failure that determines the main outcome

suppressed
→ additional failure that occurred during cleanup
  while another failure was already primary
```

### Observable example

```java
final class DemoResource implements AutoCloseable {
    @Override
    public void close() {
        throw new IllegalStateException("close failed");
    }
}

try (DemoResource resource = new DemoResource()) {
    throw new IllegalArgumentException("body failed");
}
```

An outer catch can inspect:

```java
catch (Exception ex) {
    System.out.println(ex.getMessage());

    for (Throwable suppressed : ex.getSuppressed()) {
        System.out.println("suppressed: " + suppressed.getMessage());
    }
}
```

Conceptually:

```text
primary   = IllegalArgumentException("body failed")
suppressed[0]
          = IllegalStateException("close failed")
```

This is direct evidence for why TWR preserves failure information better than naive manual cleanup.

## <a id="get-suppressed">Inspecting Suppressed Exceptions</a>

API:

```java
Throwable[] suppressed = ex.getSuppressed();
```

A throwable may contain **multiple** suppressed failures.

With three resources:

```text
A opened first
B opened next
C opened last
```

closing runs:

```text
C → B → A
```

If the body already failed and both `C.close()` and `A.close()` fail:

```text
body failure
→ primary

C close failure
→ suppressed

A close failure
→ suppressed
```

The diagnostic order follows the cleanup failures as they are encountered.

### Suppressed is not the same as Cause

They answer different questions:

```text
cause
→ which lower-level failure led to this exception
  through causal/abstraction chaining?

suppressed
→ which additional failure occurred while another
  failure was already the primary outcome?
```

Example:

```text
RuntimeException
cause
└── IOException

IOException
suppressed
└── close failure
```

A throwable can have both a cause and suppressed failures.

> Advanced detail: a `Throwable` subclass can disable suppression through `Throwable`'s protected constructor. In that unusual case, suppressed failures are not recorded. This matters mainly to library authors; ordinary exceptions use the normal suppression-enabled model.

### Should business logic depend on suppressed failures?

Usually not.

Suppressed exceptions are primarily **diagnostic context**. Business policy should normally rely on stable types and structured contract data rather than rules such as “do X when exactly two suppressed failures exist”.

## <a id="close-failure">Close Failure</a>

There are three useful cases.

### Case 1 — body succeeds, close fails

```text
body succeeds
→ close throws B
→ B becomes the primary exception
```

The overall operation fails because resource cleanup did not complete successfully.

### Case 2 — body fails, close fails

```text
body throws A
→ close throws B
→ A is primary
→ B is suppressed on A
```

### Case 3 — multiple closes fail, body succeeds

For:

```text
A opened first
B opened later
```

the close order is:

```text
B → A
```

If both closes throw:

```text
B close failure
→ primary close failure

A close failure
→ suppressed on B's failure
```

The first failure encountered during closing becomes primary when there was no earlier body failure; later close failures are preserved as suppressed.

### Why manual cleanup is easy to get wrong

```java
try {
    use();
} finally {
    close(); // may replace the failure from use()
}
```

Reproducing TWR's behavior correctly for multiple resources, partial initialization, and multiple close failures is non-trivial.

Suppression is therefore not an obscure API detail. It exists to solve a concrete failure-preservation problem.

### REMEMBER

```text
cause
→ causal / translation chain

suppressed
→ additional failure preserved without replacing the primary failure

try-with-resources
→ manages cleanup suppression semantics automatically
```

Before leaving try-with-resources, the final part covers a Java 9 syntax convenience: using an existing `final` or effectively-final variable directly as a resource.

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

The next chapter moves from resource/failure mechanics to API and domain design: when does a custom exception type add real meaning?
