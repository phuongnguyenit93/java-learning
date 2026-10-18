# Exception Handling Synthesis

## <a id="exception-synthesis">Trace One Failure End to End</a>

The module's mechanisms now connect into a two-layer model:

```text
API design / compile time
→ choose the exception type and checked/unchecked contract
→ a checked exception may force callers to catch or declare it

runtime
→ the exception is thrown, caught, propagated, cleaned up around, translated, or recovered from
```

The runtime flow can then be traced as:

```text
failure occurs
        ↓
throw
        ↓
if still unhandled: propagation + stack unwinding
        ↓
while scopes are exited: `finally` / try-with-resources cleanup still runs
        ↓
reach the responsible layer:
catch / recover / retry / translate / terminate
        ↓
if wrapping: preserve cause
if `close()` in try-with-resources also fails: preserve suppressed failures
        ↓
log/observe at the responsible boundary
```

### End-to-end example

Suppose `OrderRepository` owns an I/O resource, reads data, and raises an `IOException`. Across the module's model:

```text
OrderRepository
→ read operation raises IOException
→ try-with-resources closes the owned resource before IOException leaves the repository
→ if close() also fails, that close failure is suppressed on the IOException
        ↓
IOException propagates to OrderService

OrderService
→ cannot recover here
→ translates to OrderLoadException
→ preserves IOException as the cause

Controller / application boundary
→ receives OrderLoadException
→ maps the outcome for the caller
→ logs once with the full causal chain
```

Each layer does only the work it owns. The repository owns and closes its resource before the failure leaves that scope; the service changes failure vocabulary when the abstraction changes; the final boundary decides the external outcome and records the failure.

If `OrderService` merely wraps `IOException` in another exception without adding meaning, context, or a new handling contract, that wrapper adds no real value.

A wrapping decision can be summarized as:

```text
abstraction does not change
and no new context or handling policy is added
→ prefer rethrowing or continued propagation

abstraction changes
or structured context / a new handling category is added
→ wrap with the appropriate exception type + preserve the cause
```

The goal of exception design is not “catch more to be safer”. The goal is to **place failure policy at the right layer, preserve information, and keep success/failure semantics truthful**.
