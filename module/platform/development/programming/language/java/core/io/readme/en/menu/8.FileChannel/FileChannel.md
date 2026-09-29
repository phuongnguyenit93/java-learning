# FileChannel and Random Access

`FileChannel` is a file-oriented channel. It still moves bytes through `ByteBuffer`, but it also has a **file position**, positional operations, channel-to-channel transfer, file locking, and memory mapping. This is where the Buffer/Channel model from the previous chapter enables capabilities that a purely sequential stream does not express directly.

~~~java
Path file = Files.createTempFile("channel-demo-", ".txt");

try (FileChannel channel = FileChannel.open(
        file,
        StandardOpenOption.READ,
        StandardOpenOption.WRITE
)) {
    // work with the channel
}
~~~

## <a id="filechannel-random-access">Random Access with FileChannel</a>

Streams naturally encourage sequential processing from beginning to end. A `FileChannel` has a **file position**:

~~~java
long current = channel.position();
channel.position(10);
~~~

`read(buffer)` reads from the current position and advances it by the number of bytes read. `write(buffer)` writes at the current position and advances it by the number of bytes actually written.

For example, write a UTF-8 payload and then return to the start:

~~~java
ByteBuffer data =
        StandardCharsets.UTF_8.encode("Hello FileChannel");

while (data.hasRemaining()) {
    channel.write(data);
}

channel.position(0);
~~~

Random access is even clearer with positional read/write:

~~~java
ByteBuffer part = ByteBuffer.allocate(5);
int read = channel.read(part, 6);
~~~

The `read(buffer, position)` overload reads at the requested offset **without changing the channel's shared file position**. `write(buffer, position)` has the same property for writes.

When a positional write may consume only part of the buffer, the next file offset must advance by the number actually written:

~~~java
ByteBuffer patch = StandardCharsets.UTF_8.encode("JAVA");
long offset = 0;

while (patch.hasRemaining()) {
    int written = channel.write(patch, offset);
    if (written <= 0) {
        throw new IOException("Positional write made no progress");
    }
    offset += written;
}
~~~

`FileChannel` also exposes file size and truncation:

~~~java
long size = channel.size();
channel.truncate(100);
~~~

`truncate(100)` shortens a file that is larger than 100 bytes. It does not automatically extend a smaller file to 100 bytes.

When an application has a **durability** requirement — how strongly written data is expected to survive failures such as a process/JVM stop or power loss, within the guarantees of the storage system — `force(...)` can request that updates be pushed to storage:

~~~java
channel.force(true);
~~~

The boolean controls whether metadata must also be requested: `force(false)` focuses on file-content updates, while `force(true)` also requests the necessary metadata updates. This operation has a cost and its final guarantees still depend on the **storage stack**, meaning the layers from the JVM and operating system through the filesystem, caches, and physical storage device. Do not call it after every small write merely as a generic “safety” measure; define the durability requirement first.

`FileChannel.force(...)` is also not a replacement for `MappedByteBuffer.force()`. When data is modified through a memory-mapped buffer, the mapped buffer has its own `force()` operation for requesting that mapped changes be written to storage.

`RandomAccessFile` is the older `java.io` API for the same broad family of random-access file problems. It exposes a file pointer moved with `seek(...)` and implements `DataInput`/`DataOutput`, which can be convenient in legacy code or code that deliberately uses that primitive-oriented style. `FileChannel` fits better when the problem needs `ByteBuffer`, positional operations that do not change the shared position, transfer, locking, or mapping. `RandomAccessFile.getChannel()` returns a channel associated with the same file, and the two APIs have linked file positions, so mixing both abstractions in the same flow requires care.

## <a id="asynchronous-filechannel-boundary">The AsynchronousFileChannel Boundary</a>

`AsynchronousFileChannel` is still a file-oriented channel, but its read/write operations are **initiated and then completed asynchronously** instead of requiring the caller to wait at the `read` or `write` call itself.

Unlike `FileChannel`, this API does not center on one mutable shared file position. Each read or write names an explicit file offset:

~~~java
try (AsynchronousFileChannel channel =
             AsynchronousFileChannel.open(path, StandardOpenOption.READ)) {
    ByteBuffer buffer = ByteBuffer.allocate(4096);
    Future<Integer> pending = channel.read(buffer, 0);

    // The program can do other work here.
    int read = pending.get();
}
~~~

`Future<Integer>` is one completion style. Calling `get()` waits when the operation has not completed yet, so code that immediately calls `get()` after `read(...)` gives up most of the benefit of separating initiation from waiting.

The API also supports `CompletionHandler`, where Java invokes a callback after the operation succeeds or fails:

~~~java
channel.read(buffer, 0, null,
        new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer count, Void ignored) {
                // use the result after the read completes
            }

            @Override
            public void failed(Throwable error, Void ignored) {
                // handle failure
            }
        });
~~~

Writes offer the same two styles and also use an explicit file position. As with synchronous channels, the completed byte count may be smaller than the buffer's remaining data, so multi-step logic must follow the actual count/`position` rather than assume one operation handled the entire payload.

Lifecycle remains explicit: `AsynchronousFileChannel` is `AutoCloseable`; its owner must keep the channel open for the operations the application still needs and close it when use ends. Code must also avoid modifying or reusing a `ByteBuffer` region participating in an asynchronous operation until that operation has completed under the application's contract.

A useful selection boundary is:

~~~text
FileChannel
→ caller performs blocking/synchronous file I/O
→ control flow is usually simpler

AsynchronousFileChannel
→ operations may complete later
→ useful when the architecture can actually do other work while I/O is pending
→ completion, cancellation, and coordination become part of the design
~~~

Asynchronous I/O is not automatically faster and does not automatically solve concurrent access. When operations overlap on the same file region or in-memory data, the application still needs an explicit coordination policy. Executors, callback composition, cancellation, memory visibility, and deeper concurrency models belong in the concurrency module; the core concern here is the asynchronous I/O contract and the lifetime of the channel and buffers.

## <a id="filechannel-transfer">transferTo and transferFrom</a>

When the goal is simply to move bytes between channels, application code may not need to copy every chunk through its own `ByteBuffer`. `FileChannel` provides:

~~~java
long moved = source.transferTo(position, count, target);
~~~

and:

~~~java
long moved = target.transferFrom(source, position, count);
~~~

Some JVM/operating-system combinations can optimize this data path and reduce copying through user space. That is an optimization opportunity, not a promise that every environment uses zero-copy.

The correctness rule matters more: **a single transfer may move fewer bytes than requested**. Always use the returned count:

~~~java
long position = 0;
long size = source.size();

while (position < size) {
    long moved =
            source.transferTo(position, size - position, target);

    if (moved <= 0) {
        throw new IOException("Transfer made no progress");
    }

    position += moved;
}
~~~

The point is that `moved == 0` must not be interpreted as “the copy is complete.” For a target type that can temporarily make no progress, a real policy may retry or fall back; in this blocking file-to-file example, reporting a no-progress failure avoids an infinite loop and avoids returning a silently incomplete copy.

For ordinary file copying, `Files.copy` is often simpler and communicates intent better. `transferTo/transferFrom` becomes more relevant when the code already operates at channel level or profiling shows the transfer path is worth optimizing.

## <a id="file-lock-boundary">The File Lock Boundary</a>

A **file lock** is a coordination request to the operating system/filesystem that marks a whole file or byte region as being held under an agreed access rule by a JVM/process. Its main role is to help **multiple processes coordinate access to the same file** when the participating programs honor the locking protocol.

The two common modes are:

- an **exclusive lock**, which requests sole conflicting access to the region so another conflicting lock should not coexist;
- a **shared lock**, which allows compatible shared holders on the region when the platform supports shared locking, while still conflicting with an exclusive lock.

A requested shared lock **may be converted to an exclusive lock** on a platform that does not support shared locks. If the actual acquired mode matters, inspect `FileLock.isShared()` rather than assuming the requested boolean was preserved.

A `FileChannel` can request a lock over a file region:

~~~java
try (FileLock lock = channel.lock()) {
    // work coordinated through the file lock
}
~~~

Code can also lock a range and request shared/exclusive behavior:

~~~java
try (FileLock lock =
             channel.lock(position, size, false)) {
    // false requests an exclusive lock
}
~~~

With that coordination role established, the boundaries still matter:

- Java file locks are held on behalf of the **entire JVM**, not as a monitor for one Java thread. They are not the mechanism for coordinating threads inside the same JVM.
- Operating systems and filesystems differ in how strongly they enforce locks and whether other software treats them as advisory. For portable correctness, treat locking as a coordination protocol that participating processes must honor.
- Overlapping locks in the same JVM can cause `OverlappingFileLockException`.
- A lock does not turn multiple file operations into a database transaction or business transaction.
- A `FileLock` has a lifetime and must be released, typically with try-with-resources.

`tryLock()` lets code attempt acquisition without waiting:

~~~java
FileLock lock = channel.tryLock();

if (lock != null) {
    try (lock) {
        // protected work
    }
}
~~~

If the real problem is distributed coordination among services, a transaction across several resources, or business-level consistency, file locking is the wrong abstraction. Its scope is coordination around a file/channel.

## <a id="memory-mapped-boundary">The Memory-Mapped File Boundary</a>

`FileChannel.map` maps a file region into memory and returns a `MappedByteBuffer`. The example deliberately maps at most one 64 MiB window:

The main modes are `READ_ONLY`, `READ_WRITE`, and `PRIVATE`; `PRIVATE` uses copy-on-write semantics so the process's changes are not propagated as ordinary shared file updates.

~~~java
long regionSize = Math.min(channel.size(), 64L * 1024 * 1024);

if (regionSize > 0) {
    MappedByteBuffer mapped = channel.map(
            FileChannel.MapMode.READ_ONLY,
            0,
            regionSize
    );

    byte first = mapped.get(0);
}
~~~

Here, **virtual memory** is the operating-system mechanism that maps the address space seen by a process to RAM and data backed by storage. The operating system manages memory in blocks called **pages**; when code accesses a page that is not currently resident in RAM, a **page fault** occurs so the operating system can load or map the needed page.

The mental model is:

~~~text
file on storage
↕ operating-system virtual memory
MappedByteBuffer
↕
Java code accesses it like a buffer
~~~

Memory mapping can be useful for large files with substantial random access because the operating system manages page loading. The I/O cost has not disappeared; it appears through virtual-memory behavior such as page faults when a page is not resident in RAM.

Important boundaries include:

- A mapping has a specific offset and size; code must still respect its range.
- With Java 21's `map(MapMode, long, long)` overload, one mapped region cannot be larger than `Integer.MAX_VALUE`; very large files may need windowed mappings.
- Once successfully created, a mapping remains valid independently of whether the `FileChannel` stays open; closing the channel does **not** automatically unmap or invalidate an existing `MappedByteBuffer`.
- Concurrent truncation or mutation of the underlying file can make mapped access fail or become platform-sensitive.
- `MappedByteBuffer` has no standard application-facing `close()` operation that deterministically unmaps the region at a chosen source line, so its lifetime is less explicit than an ordinary channel.
- For writable mappings, `force()` can request that changes be written to storage, but real durability still has to match the system's requirements.
- Mapping is most compelling for workloads with meaningful large-file random access; small or sequential files are usually simpler with ordinary streams/channels.

Memory mapping is therefore a specialized tool rather than the default for file I/O.

After `FileChannel`, we have seen many objects tied to resources outside the JVM: streams, channels, directory streams, and file locks. The next chapter focuses on a design question more important than syntax: **who owns the resource, and when must it be closed?**
