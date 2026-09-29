# Suppressed Exceptions

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
OrderLoadException
cause
└── IOException

IOException
suppressed
└── close failure
```

A throwable can have both a cause and suppressed failures.

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

The next chapter moves from runtime mechanics to API/domain design: when does a custom exception type add real meaning?