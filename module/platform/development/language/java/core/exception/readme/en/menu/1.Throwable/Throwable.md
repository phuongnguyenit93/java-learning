# Throwable Hierarchy

## <a id="throwable-hierarchy">Throwable hierarchy</a>
`Throwable` is the root of Java's throw/catch hierarchy. Its two broad branches are `Error` and `Exception`; `RuntimeException` is the unchecked-exception branch under `Exception`. Only `Throwable` instances can be thrown directly by Java's exception mechanism.

## <a id="error-vs-exception">Error vs Exception</a>
`Error` usually signals serious JVM/environment conditions or linkage/assertion failures that ordinary application code is not expected to recover from generically. `Exception` represents conditions application/library APIs may model, handle, translate, or propagate. Catching `Throwable` broadly can accidentally swallow fatal conditions and is rarely appropriate.

## <a id="stack-trace-cause">Stack trace, cause and causal chain</a>
A throwable records a stack trace showing call frames near creation/throw and may reference a cause. Wrapping should normally preserve the original throwable as the cause so diagnostics retain the chain from high-level context to low-level failure. Stack traces are diagnostic evidence, not a stable machine API.
