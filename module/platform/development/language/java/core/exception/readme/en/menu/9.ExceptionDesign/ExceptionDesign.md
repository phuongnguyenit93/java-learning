# Exception Design

Knowing `try/catch` syntax is not enough to design a good failure flow. The larger question is:

```text
Which layer has enough context
and enough responsibility
to do something meaningful with this failure?
```

Good exception design preserves three things at once:

```text
correct control flow
+ correct abstraction
+ sufficient diagnostics
```

## <a id="exception-boundaries">Translate at Abstraction Boundaries</a>

Low-level failures often use implementation vocabulary:

```text
SQLException
IOException
SocketTimeoutException
```

A higher layer may need application/domain vocabulary:

```text
OrderRepositoryException
DocumentLoadException
PaymentUnavailableException
```

Example:

```java
Order loadOrder(long orderId) {
    try {
        return repository.load(orderId);
    } catch (SQLException ex) {
        throw new OrderRepositoryException(
                "Cannot load order " + orderId,
                ex
        );
    }
}
```

### WHY TRANSLATE?

If a service contract exposes `SQLException` directly, callers become coupled to the storage technology.

If the repository later moves from JDBC to files or a remote API, that infrastructure change can leak into callers.

Translation creates a boundary:

```text
implementation detail
→ ends at the boundary

application meaning
→ continues to callers
```

But translation must preserve the cause; otherwise abstraction improves while diagnostics become worse.

### Do not wrap at every layer

Avoid chains such as:

```text
SQLException
→ RepositoryException
→ ServiceException
→ ControllerException
```

when every wrapper merely renames the same failure.

Wrap when a layer adds real value such as:

- abstraction meaning;
- a handling category;
- useful context;
- a stable public contract.

## <a id="do-not-swallow">Do Not Swallow Failures</a>

Anti-pattern:

```java
try {
    run();
} catch (Exception ex) {
    // ignored
}
```

Outer code may continue as though the operation succeeded even though the expected state was never reached.

Result:

```text
failure occurs
→ signal is erased
→ caller observes false success
→ later bug appears far from the original cause
```

### Intentional ignore is different from accidental swallowing

Sometimes a specific failure is genuinely harmless:

```java
try {
    deleteTemporaryFile();
} catch (NoSuchFileException ex) {
    // the file is already absent; desired outcome is satisfied
}
```

The difference is:

- specific type;
- explicit reason;
- resulting state is still valid;
- narrow scope.

An empty `catch (Exception)` is not a substitute for that reasoning.

## <a id="logging-boundary">Log at the Responsible Boundary</a>

A common anti-pattern is:

```text
repository catches → logs → rethrows
service catches    → logs → rethrows
controller catches → logs → maps outcome
```

One failure produces several nearly identical stack traces.

### Useful heuristic

```text
layer that only rethrows/translates
→ preserve context/cause
→ usually do not log the same failure again

layer that terminates the request/job/message
→ has the final request/job/business context
→ usually logs/records the failure once
```

This is not an absolute law. A layer may emit a metric or audit event without dumping the same full exception again.

A better question than “should I log here?” is:

```text
What new information does this record add,
and which layer owns the final failure outcome?
```

### Do not log and then discard the cause

Weak:

```java
catch (SQLException ex) {
    log.error("database failed");
    throw new OrderRepositoryException("load failed");
}
```

If translation is needed, preserve the cause:

```java
catch (SQLException ex) {
    throw new OrderRepositoryException("load failed", ex);
}
```

The final boundary can log the wrapper and its causal chain once.

## <a id="exception-as-control-flow">Exceptions and Control Flow</a>

Exceptions model **abnormal completion**. They should not replace ordinary branches that can be expressed directly.

Harder to read:

```java
try {
    return values.get(index);
} catch (IndexOutOfBoundsException ex) {
    return null;
}
```

when “index does not exist” is actually a normal state the API can check clearly.

Often clearer:

```java
if (index < 0 || index >= values.size()) {
    return null;
}
return values.get(index);
```

### Parsing boundaries can legitimately use exceptions

```java
try {
    return Integer.parseInt(text);
} catch (NumberFormatException ex) {
    return defaultValue;
}
```

Here the parsing API itself reports invalid input via an exception, and a small boundary has a clear fallback policy.

The principle is not “exceptions are slow, therefore never use them”. The first concern is semantics:

```text
normal branch
→ ordinary control structure

abnormal completion
→ exception
```

Performance may matter in a hot path, but readability and contract are the primary design reasons.

## <a id="cleanup-and-recovery">Cleanup, Recovery, Retry and Propagation</a>

These are different responsibilities.

### Cleanup

```text
goal
→ release owned resources / perform mandatory exit work

mechanisms
→ try-with-resources
→ finally when appropriate
```

Cleanup does not mean the operation recovered.

### Recovery

Recovery means applying a real strategy that still produces a valid outcome.

Example:

```text
primary configuration file is missing
→ use a documented default configuration
```

Catching a failure and returning an arbitrary value is not recovery if callers cannot distinguish real data from fabricated success.

### Retry

Retry makes sense only when the failure may be **transient** and the operation is safe to attempt again.

Before retrying, ask:

```text
Is the failure plausibly transient?
Is the operation idempotent or otherwise protected from duplicate side effects?
Is retry bounded?
Does the policy account for backoff, cancellation, and timeout limits?
```

Avoid:

```java
while (true) {
    try {
        sendPayment();
        break;
    } catch (Exception ex) {
        // retry forever
    }
}
```

Unbounded retry can turn a failure into overload and can duplicate side effects.

For this Java Core module, the key idea is that **retry is a policy chosen at a boundary with enough context**, not an automatic response to every exception.

### Propagation

If the current layer cannot:

- recover;
- translate meaningfully;
- add needed context;
- terminate the operation;
- clean up resources it owns;

then propagation may be the correct choice.

### Decision model

```text
Exception reaches this layer
        ↓
Do I own resources that need cleanup?
        → yes: clean them up safely

Can I produce a valid recovered outcome?
        → yes: recover

Is the failure transient and retry safe?
        → yes: retry with a bounded policy

Does the abstraction change here?
        → yes: translate + preserve cause

Is this the final request/job boundary?
        → yes: map outcome + log/observe appropriately

None of those responsibilities apply?
        → propagate
```

### End-to-end mental model

```text
failure occurs
        ↓
throw
        ↓
propagate + stack unwinding
        ↓
cleanup must still participate
        ↓
responsible layer:
catch / recover / retry / translate / terminate
        ↓
if wrapping: preserve cause
if cleanup also fails: preserve suppressed failures
        ↓
log/observe at the responsible boundary
```

The goal of exception design is not “catch more to be safer”. The goal is to **place failure policy at the right layer, preserve information, and keep success/failure semantics truthful**.