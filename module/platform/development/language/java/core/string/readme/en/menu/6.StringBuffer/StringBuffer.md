# StringBuffer

## <a id="buffer-synchronization">StringBuffer synchronization</a>
`StringBuffer` is a mutable character sequence similar to `StringBuilder`, but its public mutating/reading operations are synchronized. That provides per-method mutual exclusion for one buffer instance and is why it is generally slower than an uncontended builder.

## <a id="builder-vs-buffer">StringBuilder vs StringBuffer trade-off</a>
Use `StringBuilder` for normal local, single-thread-confined text construction. Choose `StringBuffer` only when its synchronized per-operation contract matches a real sharing requirement. Often a better design is not to share a mutable text accumulator across threads at all.

## <a id="thread-safety-boundary">Why synchronized methods do not solve all composition concerns</a>
Several individually synchronized calls are not automatically one atomic compound action. A check-then-append sequence can still interleave unless external synchronization protects the whole invariant. Full concurrency design belongs to concurrency modules; the important boundary here is method-level synchronization vs multi-step atomicity.
