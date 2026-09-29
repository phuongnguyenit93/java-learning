# Buffered I/O

The previous chapters already have streams that can move data. A practical problem remains: if an application performs many tiny operations against an underlying resource, the cost of crossing layers or reaching the operating system can outweigh the amount of data handled per call. Buffering batches small operations into larger ones.

## <a id="buffering-purpose">Why Buffering Helps</a>

A buffer is temporary memory placed between application code and the underlying I/O resource/layer. A **wrapper** is an object that surrounds another stream or writer, adds behavior, and forwards data to the wrapped object underneath.

```text
many small reads                  fewer larger reads
application <── buffer <───────────────────────── source

many small writes                 fewer larger writes
application ──> buffer ─────────────────────────> sink
```

On input, a wrapper can fetch a block from the underlying stream and satisfy several smaller application reads from memory; fetching the next block into the buffer is often called a **refill**. On output, it can collect several small writes before forwarding a larger block.

The usual benefit is fewer crossings of a more expensive **boundary**, meaning the point where code must call into an underlying I/O/operating-system layer. Buffering does not change the payload and does not guarantee every algorithm becomes faster; the result depends on the source/sink, operation sizes, and whether another layer already buffers efficiently.

For example:

```java
for (byte value : data) {
    out.write(value);
}
```

If `out` is a `BufferedOutputStream`, those small `write` calls can be collected before data is passed to the underlying stream.

## <a id="flush-semantics">What flush() Means</a>

With buffered output, data recently written by the application may still live only in the wrapper's memory. `flush()` asks the stream or writer to push pending data to the **next layer underneath it**.

```text
application
    ↓ write
buffer
    ↓ flush
underlying stream/writer
    ↓
OS / device / peer
```

`flush()` matters when a receiver must observe data **before the stream is closed**, for example:

- a request/response protocol is waiting for the current message;
- an interactive program must display a prompt now;
- a long-lived writer has completed one logical batch.

For example:

```java
try (java.io.BufferedWriter writer =
         new java.io.BufferedWriter(
             new java.io.OutputStreamWriter(
                 socketOutputStream,
                 java.nio.charset.StandardCharsets.UTF_8))) {
    writer.write("PING");
    writer.newLine();
    writer.flush(); // push pending characters downstream now
}
```

`socketOutputStream` is an example variable representing an output stream already obtained from a socket; it is not a special JDK API name.

`flush()` does **not** mean the data is physically durable on disk. It only pushes data through buffers controlled by the current abstraction toward the next layer. File durability involves other mechanisms such as the filesystem, `FileDescriptor.sync()`, or `FileChannel.force(...)`.

Normal output wrappers flush what they need to while closing. With a short-lived resource in `try-with-resources`, calling `flush()` immediately before `close()` purely by habit is usually redundant. An explicit flush is useful when code needs a visibility or protocol boundary while the resource remains open.

## <a id="buffer-size-tradeoff">Buffer Size Trade-offs</a>

A small buffer may refill or flush frequently. A larger buffer can reduce underlying calls, but it consumes more memory per active stream and eventually provides diminishing returns.

**Throughput** means how much data the program processes per unit of time. **Latency** is how long one operation takes from start to completion. A **bottleneck** is the stage limiting overall performance. A larger buffer is useful only when measurement shows that it improves throughput/latency or relieves the relevant bottleneck.

```text
very small buffer
→ more calls to the underlying layer

reasonable buffer
→ useful batching with moderate memory

very large buffer
→ more memory with no guaranteed throughput gain
```

With thousands of concurrent streams, extra tens of kilobytes per wrapper can become a meaningful memory cost. For a large sequential file copy, a larger buffer may help if measurements show call frequency is the bottleneck.

Java buffered wrappers provide sensible default sizes for general use. Change the size when the workload, latency/throughput goals, or benchmarks provide a concrete reason rather than from a universal rule.

## <a id="buffered-wrappers">Buffered Wrappers</a>

Java uses wrapper objects to preserve the stream/reader contract while adding buffering:

```text
bytes:
FileInputStream  → BufferedInputStream
FileOutputStream → BufferedOutputStream

text:
InputStreamReader  → BufferedReader
OutputStreamWriter → BufferedWriter
```

A buffered UTF-8 round trip:

The `Path`/`Files` calls in this example only create and clean up a temporary file. Learners do not need the filesystem API yet; the point here is the buffering wrappers and the writer/reader chain.

```java
java.nio.file.Path temp = java.nio.file.Files.createTempFile("buffered-", ".txt");
String expected = "Xin chào Java ☕";

try (java.io.BufferedWriter writer =
         new java.io.BufferedWriter(
             new java.io.OutputStreamWriter(
                 new java.io.FileOutputStream(temp.toFile()),
                 java.nio.charset.StandardCharsets.UTF_8))) {
    writer.write(expected);
}

String actual;
try (java.io.BufferedReader reader =
         new java.io.BufferedReader(
             new java.io.InputStreamReader(
                 new java.io.FileInputStream(temp.toFile()),
                 java.nio.charset.StandardCharsets.UTF_8))) {
    actual = reader.readLine();
}

System.out.println(expected.equals(actual)); // true
java.nio.file.Files.deleteIfExists(temp);
```

Closing the outer wrapper closes its underlying chain according to these classes' contracts. Code therefore normally owns and closes the outermost wrapper rather than separately closing every layer.

Adding more buffering layers is not automatically faster. If one layer already buffers effectively, another buffer may only add memory and copying. Choose wrappers for capabilities the code actually needs: byte buffering, line-oriented text, charset conversion, or another behavior.

The next chapter changes focus from data movement to file identity: `java.io.File` is not a data stream but a legacy pathname/filesystem abstraction.
