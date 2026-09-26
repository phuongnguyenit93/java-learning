# Exception Design

Knowing `try/catch` syntax is not enough. The larger design question is: **which layer has enough context and responsibility to do something meaningful with a failure?**

## <a id="exception-boundaries">Translate at Abstraction Boundaries</a>

Low-level exceptions often use implementation vocabulary such as `SQLException`, `IOException`, or `SocketTimeoutException`.

A higher layer may translate them to application/domain vocabulary such as `OrderRepositoryException` or `PaymentUnavailableException`.

Translation decouples callers from infrastructure details, but the original cause should be preserved.

## <a id="do-not-swallow">Do Not Swallow Failures</a>

Avoid empty broad catches:

```java
try {
    run();
} catch (Exception ex) {
    // ignored
}
```

Silently treating a failed operation as success makes state and behavior difficult to reason about.

If a specific failure is intentionally ignored, the reason and scope should be explicit.

## <a id="logging-boundary">Log at the Responsible Boundary</a>

Logging the same exception at every layer and rethrowing it creates duplicates.

A useful heuristic is:

```text
layer that handles/terminates the request or job
→ usually logs once with full context

layer that only translates/rethrows
→ usually preserves context without logging again
```

## <a id="exception-as-control-flow">Exceptions and Control Flow</a>

Exceptions represent exceptional completion. They should not replace ordinary branches that can be expressed clearly and cheaply.

Using an exception occasionally at a parsing boundary may be reasonable; using exceptions continuously to drive loops or ordinary state transitions usually hurts readability and performance.

## <a id="cleanup-and-recovery">Cleanup, Recovery and Propagation</a>

Keep these responsibilities distinct:

```text
cleanup
→ release resources / complete mandatory cleanup

recovery
→ apply a real strategy that lets the operation continue or use an alternative

propagation
→ the current layer lacks the responsibility/context to handle the failure
```

Catch because the current layer has meaningful work to do, not merely because an exception exists.

The module's final mental model is:

```text
failure occurs
→ throw
→ propagate through the call stack
→ handle or translate where appropriate
→ always preserve cleanup responsibility
→ preserve root cause
→ log/recover at the responsible boundary
```
