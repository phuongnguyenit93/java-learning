# StringBuffer

`StringBuffer` has an API similar to `StringBuilder`, but many operations are synchronized. That provides guarantees around individual calls without automatically making every multi-step use case atomic.

## <a id="buffer-synchronization">StringBuffer Synchronization</a>

Methods such as `append` synchronize on the buffer object, preventing some races at individual method-call boundaries when several threads share the buffer.

The trade-off is synchronization overhead and possible contention.

For thread-confined/local construction, `StringBuilder` is normally the simpler choice.

### WHAT does synchronization guarantee?

Conceptually:

```text
thread A calls append(...)
        │
        └── synchronized on the StringBuffer instance

thread B calls append(...)
        │
        └── coordinates through the same monitor for that call
```

This differs from `StringBuilder`, whose API does not provide the same internal synchronization.

But synchronized methods do not make an arbitrary multi-step workflow atomic.

## <a id="builder-vs-buffer">StringBuilder vs StringBuffer</a>

```text
no shared mutable buffer across threads
→ StringBuilder

genuinely shared synchronized character buffer required
→ consider StringBuffer
```

Often the better concurrent design is to avoid sharing one mutable builder at all.

Practical comparison:

```text
single-thread / local construction
→ StringBuilder

legacy API requires StringBuffer
→ StringBuffer

genuinely shared mutable buffer needs method-level synchronization
→ consider StringBuffer, then review the whole workflow
```

Do not choose `StringBuffer` merely because “thread-safe sounds safer”. Synchronization has a cost, and shared mutable state makes design harder.

## <a id="thread-safety-boundary">Thread-safety Boundary</a>

Several synchronized methods do not make a larger read-decide-write sequence atomic.

Another thread may interleave between calls unless the caller establishes a wider synchronization boundary.

Example:

```java
if (buffer.length() < 100) {
    buffer.append(part);
}
```

`length()` and `append()` may each be synchronized, but the caller's check-then-act sequence contains **two operations**. Another thread can change the buffer between them.

If the invariant covers:

```text
read state
→ decide
→ mutate
```

the synchronization boundary must cover the whole sequence, or the design should avoid the shared mutable buffer.

Thread safety must therefore be evaluated at the **use-case operation boundary**, not only by inspecting individual synchronized methods.

Locks, concurrency primitives, and the Java Memory Model belong to the dedicated concurrency curriculum; this chapter stops at the StringBuffer API boundary.

The next chapter returns to the pool and explicit canonicalization through `String.intern()`.
