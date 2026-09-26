# Throwable

Normal control flow assumes a method completes its work and returns. Real programs also need a structured way to represent **abnormal completion**: invalid data, missing files, connection failures, cleanup failures, or broken invariants.

Java exceptions let a failure change control flow while carrying diagnostic context across call boundaries.

Learning roadmap:

```text
How does Java represent failure?
Throwable
        ↓
Which failures become part of the compile-time contract?
Checked vs Unchecked
        ↓
How are failures thrown and declared?
throw / throws
        ↓
Where does an unhandled exception go?
Propagation
        ↓
How do we handle failure and still clean up?
try / catch / finally
        ↓
How are resources closed safely?
try-with-resources
        ↓
What if body and close both fail?
Suppressed Exceptions
        ↓
When does a custom exception add meaning?
Custom Exceptions
        ↓
Where should failures be translated, logged, recovered, or propagated?
Exception Design
```

## <a id="throwable-hierarchy">Throwable Hierarchy</a>

`Throwable` is the root of Java's exception mechanism:

```text
Throwable
├── Error
└── Exception
    └── RuntimeException
```

Only `Throwable` instances can participate directly in `throw`/`catch`.

An exception is both a **control-flow event** and a **diagnostic object** carrying type, message, stack trace, and possibly a cause.

## <a id="error-vs-exception">Error vs Exception</a>

`Error` typically represents serious JVM/environment/linkage/assertion conditions that ordinary application logic should not attempt to generically recover from.

`Exception` usually represents failures an API/application may model, handle, translate, or propagate.

This is primarily a design distinction, not a syntax limitation. Catching `Throwable` too broadly can accidentally swallow conditions the application should not treat as normal recoverable failures.

## <a id="stack-trace-cause">Stack Trace and Cause</a>

A `Throwable` can carry:

- a stack trace showing call frames near creation/throw time;
- a message describing the failure;
- a cause linking to a lower-level/root failure.

When wrapping an exception, preserve the original as the cause:

```java
throw new OrderException("Cannot load order", original);
```

That lets the higher layer use its own vocabulary without losing the diagnostic chain.

The next chapter asks which failures the compiler forces callers to acknowledge.
