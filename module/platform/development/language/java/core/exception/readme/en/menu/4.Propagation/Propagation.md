# Exception Propagation

When a method does not handle an exception, the failure does not disappear. Java unwinds the call stack while looking for a matching handler.

## <a id="exception-propagation">Stack Unwinding and Propagation</a>

```text
controller()
→ service()
   → repository()
      → throw IOException
```

If `repository()` does not catch the exception, it propagates to `service()`, then to `controller()`, and so on until a handler is found or the exception escapes the thread.

Leaving those intermediate frames is **stack unwinding**.

Propagation lets lower layers report failure without needing to know whether a higher layer will recover, translate, or terminate.

## <a id="catch-selection">Catch Selection by Type</a>

Java selects the first compatible `catch` block, so more specific catches must appear before broader ones:

```java
try {
    ...
} catch (FileNotFoundException ex) {
    ...
} catch (IOException ex) {
    ...
}
```

Reversing the order would make the specific branch unreachable.

## <a id="exception-chaining">Exception Chaining</a>

At an abstraction boundary, a higher layer may translate the failure vocabulary while preserving the cause:

```java
try {
    repository.load();
} catch (SQLException ex) {
    throw new OrderRepositoryException("Cannot load order", ex);
}
```

The higher-level exception matches the current abstraction; the original cause preserves low-level diagnostics.

## <a id="lost-cause-pitfall">Lost-Cause Pitfall</a>

Avoid wrapping without the original cause:

```java
catch (SQLException ex) {
    throw new OrderRepositoryException("load failed");
}
```

That breaks the diagnostic chain and hides the original stack/context.

The next chapter covers local handling and cleanup with `try`, `catch`, and `finally`.
