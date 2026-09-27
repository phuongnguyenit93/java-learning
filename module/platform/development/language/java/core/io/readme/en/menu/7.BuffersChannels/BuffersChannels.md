# Buffers and Channels

Traditional streams encourage a “read the next byte” or “write the next byte” mental model. **NIO (New I/O)** is Java's I/O API family, primarily in `java.nio` and `java.nio.channels`, that adds another model: data is staged in a `Buffer` and moved through a `Channel`.

In this model, a `Buffer` is the **in-memory staging area** where application code places data to be written or receives data that was read. A `Channel` is the **I/O conduit/connection** that moves data between the buffer and a source or sink such as a file or socket. Separating “where the data currently lives” from “how it moves” makes readable/writable regions explicit, lets code handle transfers that make only partial progress, and enables capabilities specific to different channel types.

An NIO `Buffer` is also different from **buffering** in the Buffered I/O chapter. Buffering is the technique of batching many small I/O operations into larger ones to reduce overhead; an NIO `Buffer` is a stateful data object with values such as `position`, `limit`, and `capacity` that application code manipulates directly.

~~~text
data source
    ↓
 Channel
    ↓ read(...)
 Buffer
    ↓
application logic
~~~

For writing, the direction is reversed:

~~~text
application puts data into Buffer
    ↓
 Buffer
    ↓ write(...)
 Channel
    ↓
data sink
~~~

The hardest part for beginners is usually not the class names but **Buffer state**. Once `position`, `limit`, and `capacity` are precise, `flip`, `clear`, and `compact` become logical instead of memorized rituals.

## <a id="buffer-state">Buffer State</a>

For a `ByteBuffer`, imagine a fixed-size array of byte slots:

~~~java
ByteBuffer buffer = ByteBuffer.allocate(8);
~~~

Immediately after allocation:

~~~text
capacity = 8
limit    = 8
position = 0
~~~

The three values mean:

| Value | Meaning |
| --- | --- |
| `capacity` | Total number of elements the buffer can hold; fixed for that buffer |
| `position` | Index where the next relative read or write begins |
| `limit` | One-past-the-end boundary for the currently usable region |

They always satisfy:

~~~text
0 <= position <= limit <= capacity
~~~

While **putting data into the buffer**, `position` advances:

~~~java
ByteBuffer buffer = ByteBuffer.allocate(8);

buffer.put((byte) 10);
buffer.put((byte) 20);
buffer.put((byte) 30);
~~~

The state is now:

~~~text
capacity = 8
limit    = 8
position = 3

[10][20][30][?][?][?][?][?]
             ^
             position
~~~

`position = 3` does not mean that valid data starts at index 3. It means **index 3 is where the next relative operation begins**.

Relative `get()` operations also read at `position` and then advance it. The same buffer therefore commonly has two phases:

~~~text
fill the buffer
→ position moves forward

consume the buffer
→ position also moves forward
~~~

Moving between those phases requires updating the buffer boundaries correctly.

## <a id="byte-order-structured-binary">Structured Binary and Byte Order</a>

A `ByteBuffer` is not limited to individual `byte` operations. It also provides multi-byte primitive operations such as `putShort`, `putInt`, `putLong`, `putFloat`, `putDouble`, and matching `get...` methods. These are convenient for **structured binary** formats, where byte regions have defined meanings such as a four-byte version followed by an eight-byte timestamp.

~~~java
ByteBuffer buffer = ByteBuffer.allocate(12);

buffer.putInt(3);
buffer.putLong(1_700_000_000L);

buffer.flip();

int version = buffer.getInt();
long timestamp = buffer.getLong();
~~~

An `int` occupies four bytes and a `long` occupies eight. When several bytes form one value, code must know the **byte order (endianness)**: whether the most-significant or least-significant byte appears first in the data.

A newly created `ByteBuffer` uses `ByteOrder.BIG_ENDIAN` by default:

~~~text
value 0x01020304
BIG_ENDIAN     → 01 02 03 04
LITTLE_ENDIAN  → 04 03 02 01
~~~

If a file format or protocol specifies little-endian values, set the order before reading or writing multi-byte primitives:

~~~java
ByteBuffer little = ByteBuffer
        .allocate(8)
        .order(ByteOrder.LITTLE_ENDIAN);

little.putInt(0x01020304);
~~~

Byte order is **part of the data contract**, not a performance preference. Reading little-endian bytes as big-endian can still produce a perfectly valid Java number, but it will represent the wrong value. Byte order does not matter for isolated single-byte access; it matters when several bytes are interpreted as one primitive.

A `ByteBuffer` can also create typed views such as `asIntBuffer()` or `asLongBuffer()`. A view shares the underlying byte storage while exposing `int`- or `long`-sized elements; the byte order in effect when the view is created determines how those bytes are interpreted. Typed views can help with dense binary layouts, but the underlying model remains **byte storage + byte order + the primitive layout defined by the format**.

## <a id="flip-clear-compact">flip, clear, and compact</a>

Suppose three bytes have just been put into a buffer:

~~~text
position = 3
limit    = 8
capacity = 8
~~~

Calling `get()` now would start at index 3 instead of rereading the three bytes just written. Before consuming the data, call:

~~~java
buffer.flip();
~~~

Conceptually, `flip()` performs:

~~~text
limit = old position
position = 0
~~~

The state becomes:

~~~text
position = 0
limit    = 3
capacity = 8

[10][20][30][?][?][?][?][?]
 ^           ^
 position    limit
~~~

The readable region is now `[position, limit)`:

~~~java
while (buffer.hasRemaining()) {
    System.out.println(buffer.get());
}
~~~

After consuming all three bytes:

~~~text
position = 3
limit    = 3
~~~

If all old data has been consumed and the whole buffer should be reused for another fill cycle:

~~~java
buffer.clear();
~~~

`clear()` sets:

~~~text
position = 0
limit    = capacity
~~~

The name is easy to misread: `clear()` **does not erase or zero the old bytes**. It only resets the state so that the full region can be overwritten.

Beyond `position/limit/capacity`, a `Buffer` can also have an optional **mark**. `mark()` remembers the current position and `reset()` restores the position to that mark. Operations such as `clear()`, `flip()`, and `rewind()` discard the mark, so it is not a bookmark that survives every state transition.

`rewind()` sets `position = 0` while **keeping the current `limit`**, which is useful when the same readable region should be consumed again from the beginning. That differs from `clear()`, which reopens the full `[0, capacity)` range for a new write phase.

`compact()` serves a different case: some data remains unread. Suppose a flipped buffer contains six valid bytes and the application consumed only two:

Like `clear()`, `flip()`, and `rewind()`, `compact()` also **discards the mark** if one is currently defined.

~~~text
position = 2
limit    = 6
capacity = 8

[A][B][C][D][E][F][?][?]
       ^           ^
       position    limit
~~~

Call:

~~~java
buffer.compact();
~~~

The unread bytes `[C][D][E][F]` are copied to the beginning. The resulting state is:

~~~text
[C][D][E][F][...free space...]
             ^
             position = 4

limit = capacity = 8
~~~

The buffer can now receive more data without losing the unread bytes. After filling it again, call `flip()` before returning to consumption.

A useful cycle is:

~~~text
fill buffer
→ flip()
→ consume buffer
→ clear() if everything was consumed
  or compact() if unread data remains
→ fill again
~~~

If `put` tries to move beyond `limit`, `BufferOverflowException` may occur. If `get` tries to read beyond `limit`, `BufferUnderflowException` may occur. These failures often reveal an incorrect understanding of the current buffer state.

## <a id="channel-model">The Channel Model</a>

A `Channel` connects the program to an I/O source or sink. For a `ReadableByteChannel`, reading moves bytes **from the channel into the buffer**:

~~~java
int count = channel.read(buffer);
~~~

If `count > 0` bytes are read, the buffer's `position` advances by exactly that count. Depending on the channel type and mode, one read may produce fewer bytes than the remaining buffer space; for channels with EOF semantics, `-1` signals end of input.

A `WritableByteChannel` moves data in the opposite direction:

~~~java
int count = channel.write(buffer);
~~~

The channel consumes bytes from:

~~~text
[position, limit)
~~~

and advances `position` by the number of bytes actually written.

A copy loop using **blocking channels** (for example, `FileChannel`) makes both roles visible:

~~~java
ByteBuffer buffer = ByteBuffer.allocate(8 * 1024);

while (source.read(buffer) != -1) {
    buffer.flip();

    while (buffer.hasRemaining()) {
        target.write(buffer);
    }

    buffer.clear();
}
~~~

The inner `while (buffer.hasRemaining())` matters. A single `write` must not be assumed to consume everything remaining in the buffer; the return value reports the actual progress.

This loop deliberately uses a blocking-channel mental model. With a non-blocking channel, `read()` or `write()` may return `0` when no progress is currently possible, so the algorithm must integrate readiness/event-loop handling instead of spinning continuously with this pattern.

Compared with streams:

~~~text
Stream
→ abstraction centered on a sequential byte/character flow

Channel + Buffer
→ separates the conduit from the data staging area
→ buffer state becomes an explicit part of the algorithm
~~~

NIO is not automatically faster merely because a `Channel` is involved. The model is valuable because it enables explicit buffering, random access for suitable channels, non-blocking behavior for suitable channel families, and channel-specific operations such as transfer.

## <a id="scatter-gather-boundary">Scatter/Gather with Multiple Buffers</a>

Some byte channels, including `FileChannel`, can read or write **an array of `ByteBuffer` values in one operation**.

A **scattering read** distributes incoming bytes across several buffers in order:

~~~java
ByteBuffer header = ByteBuffer.allocate(16);
ByteBuffer body = ByteBuffer.allocate(1024);

long read = channel.read(new ByteBuffer[]{header, body});
~~~

The channel visits the buffers in order, filling an earlier buffer before continuing into a later one while data and space remain. Each buffer's `position` advances by the number of bytes that buffer actually receives.

A **gathering write** performs the opposite operation: it consumes remaining bytes from several buffers in order and writes them as one byte sequence to the channel:

~~~java
header.flip();
body.flip();

long written = channel.write(new ByteBuffer[]{header, body});
~~~

This model is useful when data naturally has multiple regions such as `header + payload`, because code does not have to copy everything into one large buffer merely to perform I/O.

The ordinary partial-operation rules still apply. The return value is the **total number of bytes actually transferred**, and one call may leave data in one or more buffers. Code must inspect each buffer's `position`/`hasRemaining()` state and continue according to the channel contract when necessary.

Scatter/gather also **does not mean that the buffers are processed in parallel**. It is still one ordered channel operation over multiple data regions. Parallelism, scheduling, and coordination among tasks or threads are separate concerns.

## <a id="bytebuffer-types">Heap and Direct ByteBuffer</a>

Two common ways to create a `ByteBuffer` are:

~~~java
ByteBuffer heap = ByteBuffer.allocate(8192);
ByteBuffer direct = ByteBuffer.allocateDirect(8192);
~~~

`allocate` creates a **heap buffer**. Its data resides in Java heap-managed memory and, for an appropriate buffer, the backing array can be accessed with `array()`.

`allocateDirect` creates a **direct buffer**. The JVM attempts to arrange memory so native I/O can use it efficiently on some data paths, potentially avoiding intermediate copies.

The main trade-offs are:

| Heap buffer | Direct buffer |
| --- | --- |
| Usually cheaper to allocate | Usually more expensive to allocate/reclaim |
| Convenient for ordinary Java logic | Can help when a long-lived buffer participates in repeated I/O |
| Often has an accessible backing array | Do not assume an accessible backing array |
| Data lives on the Java heap | Data storage is outside the Java heap |

A direct buffer is still represented by a Java object, and reclamation of its native memory is tied to the JVM's management of that buffer. It is not a resource with a public `close()` method that application code releases on demand.

Do not replace every heap buffer with a direct buffer because “direct” sounds faster. For small operations or short-lived buffers, allocation cost can outweigh any I/O benefit. Choose based on the workload and measurement.

Other buffer types such as `CharBuffer` and `IntBuffer` exist, but `ByteBuffer` is central for byte-oriented file and network I/O. The next chapter applies this exact model to `FileChannel` for random access, transfers, locking, and memory mapping.
