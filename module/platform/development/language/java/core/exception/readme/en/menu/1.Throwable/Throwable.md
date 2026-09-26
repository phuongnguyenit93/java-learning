# Throwable

Normal control flow assumes a method completes its work and returns. Real programs also need a structured model for **abnormal completion**: invalid input, missing files, connection failures, cleanup failures, or broken invariants.

Java exceptions represent that abnormal completion while carrying diagnostic context across call boundaries. Instead of forcing every method to return an extra error code, a failure can travel upward until a layer with enough responsibility decides what to do.

Throughout this module, keep one recurring story in mind:

```text
caller
  ↓
OrderService
  ↓
OrderRepository / file I/O
  ↓
failure
```

A lower layer may produce a technical failure; a higher layer decides whether to recover, translate, log, retry, terminate, or propagate it.

Learning roadmap:

```text
How does Java represent abnormal completion?
Throwable
        ↓
Which failures must callers acknowledge at compile time?
Checked vs Unchecked
        ↓
How do we throw a failure and declare that one may escape?
throw / throws
        ↓
Where does an unhandled exception go?
Propagation
        ↓
How do we handle failure and still perform cleanup?
try / catch / finally
        ↓
How are owned resources closed safely?
try-with-resources
        ↓
What if the body and cleanup both fail?
Suppressed Exceptions
        ↓
When does a custom exception type add meaning?
Custom Exceptions
        ↓
Where should failures be caught, translated, logged, retried, recovered, or propagated?
Exception Design
```

The goal is not to memorize many exception names. The goal is to understand **how failure moves through a Java program and which layer owns which decision**.

## <a id="throwable-hierarchy">Throwable Hierarchy</a>

### CONCEPT

`Throwable` is the root type of Java's exception mechanism. A simplified hierarchy is:

```text
Throwable
├── Error
└── Exception
    └── RuntimeException
```

Any value used by a `throw` statement must be compatible with `Throwable`. A throwable plays two roles at once:

```text
control-flow signal
→ normal execution is interrupted

diagnostic object
→ carries type, message, stack trace, cause, and possibly suppressed failures
```

That is why an exception is more than “an error object”. Throwing it also changes control flow.

### Where do checked and unchecked fit?

A useful compile-time classification is:

```text
Throwable
├── Error                         → unchecked
└── Exception
    ├── RuntimeException          → unchecked
    └── other Exception types     → checked
```

More precisely, subclasses of `RuntimeException` and `Error` are not subject to the checked catch-or-declare rule. The remaining throwable exception classes are checked by the language rules.

This matters because “unchecked” is broader than just `RuntimeException`; `Error` is unchecked as well.

### MECHANISM

When this executes:

```java
throw new IllegalStateException("order state is invalid");
```

normal execution stops at the `throw`. Java does not execute the next statement in that block. It begins looking for a compatible handler, and if the current method does not handle the failure, it propagates upward.

Propagation gets its own chapter later.

## <a id="error-vs-exception">Error vs Exception</a>

`Error` usually represents serious JVM, linkage, environment, or assertion conditions such as `OutOfMemoryError` or `NoClassDefFoundError`.

`Exception` usually represents failures that an API or application may model, handle, translate, or propagate, such as `IOException`, `IllegalArgumentException`, or an application-specific exception.

### WHY THE DISTINCTION MATTERS

Java syntax still permits:

```java
try {
    run();
} catch (Throwable t) {
    ...
}
```

But `catch (Throwable)` is extremely broad and also catches `Error`. Ordinary application code can then accidentally turn a serious runtime condition into something treated like a routine business failure.

Useful mental model:

```text
Exception
→ application code may have a meaningful failure policy

Error
→ do not assume ordinary application code can safely recover
```

This is design guidance, not a claim that an `Error` can never be caught. Specialized boundaries may need observation or cleanup, but `catch (Throwable)` should not be a generic default handler.

## <a id="stack-trace-cause">Stack Trace and Cause</a>

A `Throwable` commonly carries four important pieces of information:

- **type** — the failure category, such as `IOException`;
- **message** — descriptive context;
- **stack trace** — call frames that help identify the execution path;
- **cause** — a lower-level throwable that led to the current one.

Suppose a repository hits an `IOException` but the service wants an application-level vocabulary:

```java
try {
    return repository.load(orderId);
} catch (IOException ex) {
    throw new OrderLoadException("Cannot load order " + orderId, ex);
}
```

Conceptually:

```text
OrderLoadException
message = Cannot load order 42
cause
  ↓
IOException
message = connection reset
```

The causal chain can be traversed:

```java
Throwable current = ex;

while (current != null) {
    System.out.println(current.getClass().getSimpleName()
            + ": " + current.getMessage());
    current = current.getCause();
}
```

### What does a stack trace tell us?

A stack trace usually shows a path like:

```text
where the Throwable recorded its stack
        ↓
current method
        ↓
caller
        ↓
caller's caller
```

It is diagnostic data, not a stable business API to parse as text. Refactoring can change method names, line numbers, and frames.

Also avoid reusing a single exception instance as a global “error constant”. A throwable usually records stack information when it is created or when its stack is filled, so reusing the same instance can produce misleading diagnostics.

### REMEMBER

```text
Throwable
= diagnostic object
+ control-flow signal
```

The next chapter asks which failures the compiler turns into a mandatory caller-facing contract.