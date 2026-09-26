# StringBuffer

`StringBuffer` has an API similar to `StringBuilder`, but many operations are synchronized. That provides guarantees around individual calls without automatically making every multi-step use case atomic.

## <a id="buffer-synchronization">StringBuffer Synchronization</a>

Methods such as `append` synchronize on the buffer object, preventing some races at individual method-call boundaries when several threads share the buffer.

The trade-off is synchronization overhead and possible contention.

For thread-confined/local construction, `StringBuilder` is normally the simpler choice.

## <a id="builder-vs-buffer">StringBuilder vs StringBuffer</a>

```text
no shared mutable buffer across threads
→ StringBuilder

genuinely shared synchronized character buffer required
→ consider StringBuffer
```

Often the better concurrent design is to avoid sharing one mutable builder at all.

## <a id="thread-safety-boundary">Thread-safety Boundary</a>

Several synchronized methods do not make a larger read-decide-write sequence atomic.

Another thread may interleave between calls unless the caller establishes a wider synchronization boundary.

Thread safety must therefore be evaluated at the **use-case operation boundary**, not only by inspecting individual synchronized methods.

The next chapter returns to the pool and explicit canonicalization through `String.intern()`.
