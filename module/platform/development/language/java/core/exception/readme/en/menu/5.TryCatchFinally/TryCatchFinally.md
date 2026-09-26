# try, catch and finally

Propagation lets failures move upward. `try/catch/finally` lets a layer make three different decisions:

```text
try
→ execution that may fail

catch
→ handle a particular failure category

finally
→ perform mandatory work when leaving the try/catch region
```

The difficult part is not the syntax. It is the **control-flow order** when success, exceptions, `return`, and cleanup interact.

## <a id="try-catch-flow">try/catch Flow</a>

Example:

```java
try {
    load();
    process();
} catch (IOException ex) {
    recover(ex);
}
```

If `load()` succeeds:

```text
load
→ process
→ skip catch
→ continue after try/catch
```

If `load()` throws `IOException`:

```text
load
→ throw
→ process is skipped
→ find a compatible catch
→ recover
→ continue after try/catch if the handler completes normally
```

If the thrown exception does not match the handler, propagation continues.

### Catch does not automatically mean recovery

A common misunderstanding is:

```java
catch (Exception ex) {
    System.out.println("error");
}
```

Catching only means propagation was stopped there. It does **not** automatically restore a valid program state.

Catch where the current layer has meaningful work to do, for example:

- recover through a valid fallback;
- translate into the current abstraction;
- add useful context and rethrow;
- deliberately terminate the operation;
- at an outer boundary, record/map the failure.

If the layer has no useful action, propagation is often better than swallowing the failure.

### Broad catches need a clear boundary reason

`catch (Exception ex)` is not universally wrong. It may be appropriate at an application boundary that must turn application failures into one final outcome.

In middle layers, however, a broad catch can:

- capture failures the layer does not understand;
- hide programming bugs;
- destroy type-specific policy;
- make success/failure semantics ambiguous.

## <a id="finally-semantics">finally Semantics</a>

`finally` is designed for work that should happen when control leaves `try/catch`.

```java
try {
    useResource();
} finally {
    cleanup();
}
```

In normal JVM control flow it typically runs when the exit path is:

- normal completion;
- a handled exception;
- a propagating exception;
- `return`;
- relevant `break` or `continue`.

Mental model:

```text
try/catch determines the pending outcome
        ↓
before control actually leaves the construct
        ↓
finally executes
```

### `finally` is not a physical-world guarantee

“Finally always runs” should not be interpreted absolutely. Forced process/JVM termination, crashes, or external power loss can prevent cleanup code from running.

Within ordinary language-level control flow, `finally` is Java's construct for mandatory exit work.

### Prefer try-with-resources for owned resources

Manual cleanup traditionally looks like:

```java
InputStream in = null;
try {
    in = Files.newInputStream(path);
    ...
} finally {
    if (in != null) {
        in.close();
    }
}
```

This becomes subtle when the body and `close()` both fail. For `AutoCloseable` resources, try-with-resources usually models ownership more safely and clearly.

## <a id="return-finally">return/throw and finally</a>

This is where `finally` can become dangerous.

### A return value is prepared, then finally runs before the method exits

```java
int value() {
    try {
        return 1;
    } finally {
        System.out.println("cleanup");
    }
}
```

The result is still `1`, but cleanup executes before the caller receives it.

### A return in finally replaces a pending return

```java
int dangerous() {
    try {
        return 1;
    } finally {
        return 2;
    }
}
```

The result is `2`.

Flow:

```text
try prepares return 1
        ↓
finally runs
        ↓
finally creates a new return 2
        ↓
return 1 is discarded
```

### A throw from finally can hide the original exception

```java
try {
    throw new IllegalStateException("original");
} finally {
    throw new RuntimeException("cleanup failed");
}
```

The cleanup failure escapes, while the original failure may be lost under manual cleanup semantics.

This is one reason try-with-resources is safer: it preserves cleanup failures as **suppressed exceptions** instead of blindly replacing the primary failure.

Practical rule:

```text
finally
→ cleanup

avoid
→ return from finally
→ introducing a new cleanup failure when a safer resource mechanism exists
```

## <a id="multi-catch">Multi-catch</a>

When several exception types genuinely share the **same handling policy**, Java supports:

```java
try {
    importData();
} catch (IOException | SQLException ex) {
    auditAndAbort(ex);
}
```

### WHY?

Without multi-catch, code can duplicate the same handler:

```java
catch (IOException ex) {
    auditAndAbort(ex);
} catch (SQLException ex) {
    auditAndAbort(ex);
}
```

### Alternatives may not be subtype-related

This is invalid:

```java
catch (FileNotFoundException | IOException ex) {
    ...
}
```

because `FileNotFoundException` is already a subtype of `IOException`.

### Do not merge failures merely to save lines

If policies differ:

```text
FileNotFoundException
→ ask for another file

SQLException
→ report storage unavailable
```

then combining them because today's handler body looks similar loses meaning.

### REMEMBER

```text
try
→ region that may complete abruptly

catch
→ a concrete failure policy

finally
→ exit cleanup

multi-catch
→ multiple types with one genuinely shared policy
```

The next chapter replaces manual resource cleanup with try-with-resources, where resource lifecycle is part of the syntax.