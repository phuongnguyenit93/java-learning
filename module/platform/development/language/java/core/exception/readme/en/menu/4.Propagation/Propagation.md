# Exception Propagation

When a method does not handle an exception, the failure does not disappear. Java **unwinds the call stack** while looking for a compatible handler in callers above it.

This is the bridge between `throw` and `try/catch`: `throw` creates abnormal completion at one point; propagation determines where that failure goes next.

## <a id="exception-propagation">Stack Unwinding and Propagation</a>

Reuse the module's running story:

```text
controller()
   ↓
service()
   ↓
repository()
   ↓
throw IOException
```

Example:

```java
void controller() throws IOException {
    service();
}

void service() throws IOException {
    repository();
}

void repository() throws IOException {
    throw new IOException("cannot read order");
}
```

No method catches the exception, so the flow is:

```text
repository()
→ stops at throw
→ leaves repository frame

service()
→ does not continue after repository()
→ leaves service frame

controller()
→ exception continues to controller's caller
```

Leaving those frames is **stack unwinding**.

### What happens while the stack unwinds?

Leaving a frame does not mean Java skips all cleanup. Before the frame is fully left:

- an associated `finally` may still run;
- try-with-resources resources are closed according to their contract;
- a compatible handler in that frame can still stop propagation.

Example:

```java
void service() throws IOException {
    try {
        repository();
    } finally {
        System.out.println("service cleanup");
    }
}
```

`repository()` throws `IOException`, but the service `finally` executes before the exception continues upward.

### What if nobody catches it?

If an exception escapes the thread's entry point, it reaches that thread's uncaught-exception handling and the thread terminates by exceptional completion.

That does not necessarily mean “the whole JVM immediately dies”; process behavior also depends on other threads and the runtime context. The important mental model here is:

```text
no handler
→ failure continues to the thread's outer boundary
```

### WHY PROPAGATION EXISTS

A repository does not need to know:

- what a UI will display;
- which HTTP status a web boundary would produce;
- whether a batch job should retry;
- whether a higher layer will translate the exception.

The lower layer reports failure; policy belongs where enough context exists.

## <a id="catch-selection">Catch Selection by Type</a>

For a `try` with multiple handlers, Java checks them in source order and selects the first `catch` whose parameter type can accept the thrown exception.

```java
try {
    load();
} catch (FileNotFoundException ex) {
    handleMissingFile(ex);
} catch (IOException ex) {
    handleOtherIo(ex);
}
```

`FileNotFoundException` is a subtype of `IOException`, so the specific handler must come first.

This is invalid:

```java
try {
    load();
} catch (IOException ex) {
    handleIo(ex);
} catch (FileNotFoundException ex) { // unreachable
    handleMissingFile(ex);
}
```

The compiler knows every `FileNotFoundException` would already have matched the broader `IOException` handler.

### Type is a better contract than parsing messages

Avoid policies such as:

```java
catch (IOException ex) {
    if (ex.getMessage().contains("not found")) {
        ...
    }
}
```

Messages are diagnostic text and may change. If callers need different behavior, exception types or structured context are usually a stronger contract.

## <a id="exception-chaining">Exception Chaining</a>

Propagation can keep the original exception unchanged. At an abstraction boundary, however, a low-level type may leak implementation details.

Suppose a repository uses JDBC:

```java
Order load(long orderId) {
    try {
        return jdbcLoad(orderId);
    } catch (SQLException ex) {
        throw new OrderRepositoryException(
                "Cannot load order " + orderId,
                ex
        );
    }
}
```

Flow:

```text
SQLException
→ storage/JDBC vocabulary

OrderRepositoryException
→ repository/application vocabulary

cause
→ still preserves SQLException for diagnosis
```

Two things happen together:

```text
translation
→ change abstraction vocabulary

chaining
→ preserve the causal chain
```

### When should we wrap?

Wrap when the current layer creates a meaningful abstraction boundary.

Avoid unconditional wrapper chains such as:

```text
IOException
→ MyIOException
→ ServiceIOException
→ ControllerIOException
```

when each class merely renames the same failure. Wrapping is useful when it adds contract, context, handling meaning, or abstraction.

## <a id="lost-cause-pitfall">Lost-Cause Pitfall</a>

Anti-pattern:

```java
catch (SQLException ex) {
    throw new OrderRepositoryException("load failed");
}
```

The new exception no longer knows that the `SQLException` caused it.

Consequences:

```text
log
→ only sees the wrapper chain you kept

root stack trace
→ removed from the causal chain

debugging
→ loses where the database failure originated
```

When translation is useful, preserve the cause:

```java
catch (SQLException ex) {
    throw new OrderRepositoryException("load failed", ex);
}
```

### Rethrow or wrap?

If the abstraction does not change:

```java
catch (IOException ex) {
    audit(ex);
    throw ex;
}
```

may be appropriate.

If the abstraction changes:

```java
catch (IOException ex) {
    throw new OrderLoadException("Cannot load order file", ex);
}
```

may be clearer.

Do not create a new wrapper merely because a catch block exists.

### REMEMBER

```text
throw
→ starts exceptional flow

propagation
→ moves failure upward

stack unwinding
→ leaves frames while required cleanup still participates

catch
→ stops propagation where a layer owns a policy

translation + cause
→ changes vocabulary without losing the root failure
```

The next chapter examines the point where propagation is handled: how `try`, `catch`, and `finally` interact across different exit paths.