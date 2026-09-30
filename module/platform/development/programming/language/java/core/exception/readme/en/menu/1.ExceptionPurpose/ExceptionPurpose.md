# What Exceptions Are and Why Java Uses Them

## <a id="exception-purpose">What Is an Exception?</a>

Normal control flow assumes a method completes its work and returns. Real programs also need a structured model for **abnormal completion**: invalid input, missing files, connection failures, cleanup failures, or broken invariants.

Java exceptions represent that abnormal completion while carrying diagnostic context across call boundaries. Instead of forcing every method to return an extra error code, a failure can travel upward until a layer with enough responsibility decides what to do.

An exception should be understood in two ways at the same time:

```text
Throwable object
→ carries type, message, stack trace, cause, and other diagnostic context

abrupt control-flow mechanism
→ when thrown, normal execution stops and Java searches for a compatible handler
```

An API can represent failure with `boolean`, `null`, status codes, or special return values, and those choices are useful in some designs. However, when failure must cross several call layers, each caller then has to remember to inspect and forward that status, normal results and failures share the same return channel, and causal context is easy to lose. Exceptions give Java a **separate abnormal-completion channel** that can propagate through the call stack while preserving a typed failure and its cause.

That mechanism gives exceptions several roles in application design:

```text
API contract
→ communicates failure cases callers need to understand; checked exceptions may appear in a throws contract

propagation across layers
→ moves failure from the origin to a layer with enough responsibility to decide what it means

cleanup
→ lets finally / try-with-resources release resources when the success path is interrupted

diagnostics
→ preserves type, message, stack trace, cause, and suppressed failures

translation / recovery
→ a boundary may wrap a technical failure in domain language, retry, fall back, or terminate a request appropriately
```

Exceptions should not become the default mechanism for **normal control flow** when an expected branch is clearer as a condition, return value, or result type. Throwing and catching exceptions merely to end a loop or choose an ordinary branch obscures the success path and weakens the meaning of the failure mechanism.

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

Knowledge in this module follows the approved roadmap:

```text
What are exceptions and why does Java use them?
        ↓
Throwable and Java's failure hierarchy
        ↓
Checked and unchecked exception contracts
        ↓
throw, throws, and exception propagation
        ↓
Handling exceptions with try/catch/finally
        ↓
Resource safety with try-with-resources
        ↓
Designing custom exceptions
        ↓
Designing exception boundaries
        ↓
Exception handling synthesis
```

The goal is not to memorize many exception names. The goal is to understand **how failure moves through a Java program and which layer owns which decision**.
