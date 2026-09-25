# Exception Propagation

## <a id="exception-propagation">Stack unwinding and propagation</a>
When a method throws and does not handle an exception, its frame exits and the exception propagates to the caller. This continues until a matching handler is found or the thread terminates. `finally`/resource cleanup executes according to language rules during unwinding.

## <a id="catch-selection">Catch selection by type</a>
Catch clauses are tested top-to-bottom and the first assignment-compatible type handles the throwable. More specific catches must appear before broader ones; otherwise the broader catch would make later clauses unreachable.

## <a id="exception-chaining">Wrapping with preserved cause</a>
Translate a low-level exception when crossing an abstraction boundary and the caller benefits from a higher-level meaning. Preserve the original cause:

```java
catch (SQLException e) {
    throw new OrderRepositoryException("Cannot load order " + id, e);
}
```

This adds domain context without destroying diagnostics.

## <a id="lost-cause-pitfall">Lost-cause anti-pattern</a>
Creating a new exception with only `e.getMessage()` discards the original type, stack, suppressed exceptions, and causal chain. Also avoid logging then rethrowing at every layer, which creates noisy duplicate traces. Preserve cause and let the responsible boundary decide how to report it.
