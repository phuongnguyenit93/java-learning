# Throwable and Java's Failure Hierarchy

After understanding why exceptions exist, the next step is to understand how Java represents and classifies throwable failures through the `Throwable` hierarchy.

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

More precisely, subclasses of `RuntimeException` and `Error` are not subject to the checked catch-or-declare rule. Every other `Throwable` class is checked by the language rules, including the unusual case of a class that extends `Throwable` directly instead of extending `Exception`.

Ordinary application exceptions should normally extend `Exception` or `RuntimeException` rather than subclassing `Throwable` directly; the diagram intentionally focuses on the hierarchy learners encounter in normal code.

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

### The hierarchy determines catch breadth

The type declared by a `catch` selects a **branch of the hierarchy** that the handler can receive:

```text
catch (Throwable)
→ very broad: receives both Error and Exception

catch (Exception)
→ receives Exception and its subclasses
→ does not receive Error

catch (IOException)
→ narrower: receives IOException and its subclasses
```

Choosing a catch type is therefore not name memorization; it is choosing the **failure range the current layer actually owns**. Catching too broadly can pull in failures the layer does not understand, while narrower types make handling policy clearer.

The propagation chapter later covers the exact selection rules when several catch handlers are present.

## <a id="stack-trace-cause">Stack Trace and Cause</a>

A `Throwable` commonly carries four important pieces of information:

- **type** — the failure category, such as `IOException`;
- **message** — descriptive context;
- **stack trace** — call frames that help identify the execution path;
- **cause** — a lower-level throwable that led to the current one.

To observe the `cause` relationship without introducing custom exceptions yet, use a familiar wrapper:

```java
try {
    return repository.load(orderId);
} catch (IOException ex) {
    throw new RuntimeException("Cannot load order " + orderId, ex);
}
```

Conceptually:

```text
RuntimeException
message = Cannot load order 42
cause
  ↓
IOException
message = connection reset
```

`RuntimeException` is used here only to demonstrate the `cause` structure, not to recommend wrapping every `IOException` in `RuntimeException`. Custom exception design and translation decisions are covered later.

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
