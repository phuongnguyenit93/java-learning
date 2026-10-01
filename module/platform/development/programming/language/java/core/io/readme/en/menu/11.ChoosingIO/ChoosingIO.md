# Choosing the Simplest Correct I/O Abstraction

Java has several I/O APIs because different problems need different abstractions. The goal is not to memorize one “most powerful” API. Choose the simplest layer that still represents the payload, data size, access pattern, and resource lifetime correctly.

A useful decision sequence is:

~~~text
Are we handling bytes, text, or structured binary data?
→ is the data small and bounded, or potentially large?
→ sequential processing or random access/Channel behavior?
→ what byte order/framing does the binary format define?
→ which charset?
→ who owns the resource?
→ what partial state can remain after failure?
→ is there measured evidence that performance needs tuning?
~~~

## <a id="choose-stream-reader-channel">Choosing Stream, Reader/Writer, Files, Channel, or ClassLoader Resources</a>

Start with **what the data means**, not which API sounds newer.

| Problem | Typical abstraction |
| --- | --- |
| Sequential binary bytes | `InputStream` / `OutputStream` |
| Primitive binary fields with a fixed schema/order | `DataInput` / `DataOutput`, commonly through `DataInputStream` / `DataOutputStream` |
| Text as characters | `Reader` / `Writer` |
| Filesystem operations around paths | `Path` + `Files` |
| Inspect path capabilities/storage/provider | `Path.getFileSystem()` + `FileSystem` / `FileStore` |
| Monitor directory changes | `WatchService` |
| Small bounded file, one-shot convenience | `Files.readString`, `writeString`, `readAllBytes`... |
| Byte I/O that needs Buffer/Channel model | `Channel` + `ByteBuffer` |
| File position, random access, transfer, lock, map | `FileChannel` |
| Positional file I/O with asynchronous completion | `AsynchronousFileChannel` |
| Packaged resources on the classpath/module path | `Class.getResource(...)` / `Class.getResourceAsStream(...)` / `ClassLoader.getResource(...)` / `ClassLoader.getResourceAsStream(...)` |

Copying an image should not go through a `Reader` because the payload is binary:

~~~java
try (
        InputStream in = Files.newInputStream(source);
        OutputStream out = Files.newOutputStream(target)
) {
    in.transferTo(out);
}
~~~

Reading UTF-8 text line by line fits `BufferedReader`:

~~~java
try (BufferedReader reader =
             Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
    String line;

    while ((line = reader.readLine()) != null) {
        System.out.println(line);
    }
}
~~~

For a filesystem-level operation, `Files` is often clearer than constructing streams:

~~~java
Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
~~~

When the requirement is reading at a particular file offset:

~~~java
try (FileChannel channel =
             FileChannel.open(path, StandardOpenOption.READ)) {
    ByteBuffer header = ByteBuffer.allocate(16);
    channel.read(header, 128);
}
~~~

This example only demonstrates **reading at an offset**. If the contract requires a complete 16-byte header, the code must still loop using the actual read count; before consuming bytes from `header` with `get(...)`, call `flip()` to switch the buffer into read mode.

A practical rule is:

~~~text
choose the abstraction that matches the problem
→ keep the solution small and explicit
→ move to a lower-level mechanism only when its capability is required
~~~

A channel is not an “always better stream.” A reader/writer is not a text-shaped wrapper for arbitrary data. Each abstraction answers a different kind of I/O question.

Likewise, `DataInputStream` does not replace `ObjectInputStream`: it reads **primitive fields according to a format both sides already agree on**, while native object serialization carries its own object-graph/version contract. `AsynchronousFileChannel` is not “automatically faster” than `FileChannel` either; it changes how completion is organized and is useful only when the workload/architecture needs asynchronous file operations.

For filesystem work, do not assume every `Path` is the same kind of operating-system path. If code depends on POSIX permissions, atomic moves, `WatchService`, `toFile()`, or another capability, remember that the path belongs to a `FileSystem`/provider and check the capability rather than inferring it from the developer machine.

A **classpath/module-path resource** belongs to a different namespace from the filesystem. `Class.getResourceAsStream(...)` or `ClassLoader.getResourceAsStream(...)` may read content packaged inside a JAR or another source managed by a class loader, so code must not assume a resource found this way can always become a local `File` or `Path`. When the API returns a stream, normal stream-lifecycle rules still apply. Relative/root lookup rules, delegation, and resource enumeration belong to the **ClassLoader** module; the I/O boundary to retain is that **a classpath resource is not the same thing as a filesystem path**.

## <a id="memory-vs-streaming">Whole-Content vs Streaming</a>

Convenience APIs such as `readAllBytes` and `readString` are excellent when input is **small and bounded**:

~~~java
String config =
        Files.readString(path, StandardCharsets.UTF_8);
~~~

They keep code short and make whole-content processing easy. The important memory model is that the **entire logical content must fit in memory at once**:

~~~text
readAllBytes(file of size N)
→ returns a byte[] containing the whole file

readString(file)
→ returns one String containing the whole decoded text
→ implementation may also need temporary byte/character buffers while decoding
~~~

If a file can be large, has an untrusted size, or comes from a long-running source, streaming gives a bounded memory model:

~~~java
try (InputStream in = Files.newInputStream(path)) {
    byte[] buffer = new byte[8192];
    int read;

    while ((read = in.read(buffer)) != -1) {
        process(buffer, 0, read);
    }
}
~~~

Streaming does not mean “no buffer.” It usually means processing **one bounded buffer at a time** instead of retaining the full payload.

~~~text
load all
→ simple
→ good for small, bounded data

streaming
→ bounded memory
→ good for large/uncertain data
→ logic must handle chunks and partial data
~~~

A common mistake is to use `readAllBytes()` for an upload because local tests use a 20 KB file. When production receives a 2 GB file, the abstraction was wrong from the start.

Define the data bound before choosing the API. “The current example is small” is different from “the contract guarantees this input is small.”

## <a id="charset-explicit">Make the Charset Explicit</a>

When text crosses a **byte-oriented boundary** such as a file or byte stream, encoding/decoding forms the bridge:

~~~text
bytes
↔ Charset encode/decode
↔ characters / String
~~~

For such a byte-backed text format, the charset is part of the data contract. Pure in-memory character sources such as `StringReader` do not perform a byte/charset conversion. For a UTF-8 file format, say so explicitly:

~~~java
String text =
        Files.readString(path, StandardCharsets.UTF_8);

Files.writeString(
        target,
        text,
        StandardCharsets.UTF_8
);
~~~

With a stream bridge:

~~~java
try (
        InputStream in = Files.newInputStream(path);
        Reader reader =
                new InputStreamReader(in, StandardCharsets.UTF_8)
) {
    // read characters as UTF-8
}
~~~

Since Java 18, the default charset of standard Java APIs is UTF-8 unless an implementation is configured through its supported mechanism to use another default. Even so, when a file format or protocol defines a specific charset, code should pass that charset explicitly: it is part of the data contract rather than a decision that should depend on the runtime default.

Charset also explains why arbitrary binary data should not be converted into a `String`:

~~~java
byte[] png = Files.readAllBytes(imagePath);
// new String(png, UTF_8) does not make PNG bytes valid text
~~~

If the payload is binary, keep it as bytes. Use Reader/Writer/String when the payload is actually text with a defined encoding.

## <a id="io-error-handling">I/O Errors and Partial Operations</a>

I/O communicates with systems outside ordinary Java memory, so failure is a normal part of its contract: a file may be missing, permission denied, disk full, connection closed, path replaced, or an option unsupported by the filesystem.

`IOException` and subtypes such as `NoSuchFileException` and `AccessDeniedException` let code distinguish causes when the policy really differs:

~~~java
try {
    return Files.readString(path, StandardCharsets.UTF_8);
} catch (NoSuchFileException e) {
    // Application policy may use a default, report a clear
    // "missing file" result, or propagate the exception.
    throw e;
}
~~~

The important point is **not to silently turn “file missing” into an empty string**, because that makes “missing” indistinguishable from “present but empty.” Catch a failure when the code has a meaningful decision for it. Catching `IOException` and silently ignoring it often turns an I/O failure into missing data or a difficult-to-diagnose state.

Another central rule is that **failure does not imply “nothing happened.”** I/O may have progressed partially:

~~~text
write 100 KB
→ 40 KB was written
→ failure occurs
→ the target may already have changed
~~~

Channel APIs expose partial progress directly through return values. The following loop assumes a **blocking channel**. A non-blocking channel may return `0` when it cannot currently make progress, in which case readiness handling is needed instead of spinning:

~~~java
while (buffer.hasRemaining()) {
    int written = channel.write(buffer);
    if (written <= 0) {
        throw new IOException("Blocking write made no progress");
    }
    // position advances by the amount actually written
}
~~~

Copy/move/delete operations also have filesystem-specific contracts. If an application needs publishing semantics like “readers see either the old file or the new file,” one design may write a temporary file and then request an atomic move where the filesystem supports it. `ATOMIC_MOVE` still has a support boundary: unsupported atomic movement produces `AtomicMoveNotSupportedException`, and when the target already exists, replace-versus-fail behavior can be implementation-specific. The publishing policy therefore must define what the application accepts instead of assuming `REPLACE_EXISTING` has identical semantics with an atomic move everywhere.

Retries also need semantics. Retrying a read may be straightforward; retrying a write that already progressed can duplicate data unless the code knows the target offset/state. **Idempotent** means repeating the operation still leads to the same intended final result; **resumable** means work can continue from the progress already made. Do not add a generic retry around every `IOException` without knowing whether the operation has those properties.

Resource cleanup still matters when failures occur. That is why ownership and try-with-resources from the previous chapter are part of I/O error handling, not just style.

## <a id="io-performance-boundary">The I/O Performance Boundary</a>

I/O is often slower than CPU work because it crosses into filesystems, storage, or networks. That does not mean a more complicated API is automatically faster.

Common cost patterns include:

~~~text
many tiny system calls
→ buffering may help

copying data through several layers
→ channel transfer/direct buffers may help for suitable workloads

loading a very large file at once
→ streaming reduces memory pressure

large random-access workload
→ FileChannel/memory mapping may fit

flush/force too frequently
→ increases durability cost
~~~

Here a **system call** is a transition from the application into the operating system for work such as file I/O; **memory pressure** means the cost/risk of retaining too much data in memory; **contention** means several threads or processes compete for the same resource; and **durability** describes whether data has been pushed far enough down the storage stack to survive the failures the application cares about.

A sound optimization order is:

~~~text
1. Choose the correct abstraction and make the code correct.
2. Establish clear memory/resource bounds.
3. Measure the real workload with profiling, benchmarks, or metrics.
4. Identify the actual bottleneck: CPU, allocation, syscall, disk, network, or contention.
5. Optimize only where evidence points.
~~~

An 8 KB buffer is not a universal optimum. Suitable sizes depend on storage, workload, concurrency, and access pattern. Direct buffers, `transferTo`, and memory mapping all have costs of their own.

For most business code, a clear implementation using `Files`, buffered streams, or Reader/Writer is a good starting point. When measurement shows that the I/O path is a bottleneck and identifies the type of bottleneck, there is enough information to decide whether channels, direct buffers, transfer operations, or memory mapping are justified.

The entire module can now return to one coherent mental model:

~~~text
what is the payload?
→ bytes or characters

what are the source and sink?
→ file, memory, network, ...

how does data move?
→ sequential, buffered, channel-based, random access

who owns the external resource?
→ the opener usually closes it unless the contract transfers ownership

which boundaries must be explicit?
→ charset, size, partial operations, failure, compatibility, security
~~~

Good I/O design makes those decisions visible in code and adds complexity only when the problem actually requires it.
