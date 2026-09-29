# Java I/O Mental Model

## <a id="io-data-flow">What Is I/O?</a>

A program that only computes in memory keeps its working data inside the running **process**, meaning one running instance of the program managed by the operating system. When the process ends, objects, local variables, and arrays in RAM do not automatically become persistent data. The program also cannot receive keyboard input, read a file, send data over a network, or write a result somewhere else without crossing a **process boundary** between resources inside that running process and resources outside it.

**I/O (Input/Output)** is the set of mechanisms used to move data **into** a program and **out of** a program.

A useful starting model is:

```text
source ── input ──> program ── output ──> sink
```

- A **source** is where data comes from, such as a file, **socket** (a network endpoint used by a program to exchange data), keyboard, or memory region.
- A **sink** is where data goes, such as a file, socket, screen, or memory region.
- The **payload** is the data being moved.
- A **resource boundary** is where Java code starts interacting with a resource that has its own lifetime, often managed by the operating system or another component outside the process.

Suppose an application writes `Xin chào Java ☕` to a file and reads it back:

```text
String in RAM
    ↓ encode to bytes
temporary file on the filesystem
    ↓ read bytes + decode
new String in RAM
```

If the original string only stays in RAM, it disappears with the process. Writing it to a file creates a representation that can outlive the object currently stored on the **heap**, the JVM memory area where Java objects normally live.

A **filesystem** is the operating-system mechanism that organizes files, directories, and information about them. That accompanying information is **metadata**, such as whether an entry exists, its type, size, or modification time; metadata is different from the file's content bytes.

**Buffering** is different from **storage or the underlying resource**. A buffer is temporary memory used to batch data and reduce many small I/O operations; it does not by itself make data persistent. A file, socket, or device is the source or sink that the buffer helps application code communicate with.

The major terms in this module form a learning path rather than an unrelated list:

```text
I/O data flow
    ↓ data needs a transfer unit
byte stream / character stream
    ↓ many tiny operations can be expensive
buffering
    ↓ files also have names, paths, and metadata
File → Path / Files
    ↓ NIO separates stored data from the conduit that moves it
Buffer / Channel / FileChannel
    ↓ external resources need explicit lifetime ownership
resource management
    ↓ some data can be represented as an object graph
serialization
    ↓ finally choose the simplest correct abstraction
choosing I/O
```

The major components and their roles are:

| Component | Role |
| --- | --- |
| Byte streams | Read/write raw byte data; structured primitive fields additionally need an explicit type/order contract. |
| Character streams + `Charset` | Work with text and convert correctly between characters and bytes. |
| Buffering | Batch small operations into larger chunks to reduce I/O overhead. |
| `File` vs `Path` / `Files` | Represent and operate on filesystem locations/metadata; `File` is the older API and `Path`/`Files` are the newer model. |
| `WatchService` | Receive filesystem change notifications such as create/modify/delete with provider/platform boundaries. |
| `Buffer` / `Channel` / `FileChannel` | NIO's data-container and transfer model; `ByteOrder` controls multi-byte primitive interpretation and channels can support multi-buffer transfer. |
| `AsynchronousFileChannel` | Positional file I/O whose completion is asynchronous instead of keeping the calling thread blocked until completion. |
| Resource management | Decide who owns a resource and when it must be closed. |
| Serialization | Convert object state using a specific serialization format. |
| Choosing I/O | Select the simplest correct abstraction for the payload, scale, and operation pattern. |

**NIO (New I/O)** is Java's newer family of I/O APIs, including concepts such as `Buffer`, `Channel`, and `java.nio.file`. For now, treat it as another I/O abstraction family that later chapters introduce; not every NIO API is non-blocking.

The key boundary is broader than file access. A file is only one kind of source or sink. The same source → payload → sink model also applies to networks, consoles, memory streams, and other devices.

## <a id="bytes-vs-characters">Bytes and Characters</a>

Physical storage and transport ultimately move bytes, while application code often wants to work with text. Those needs create two important abstraction levels:

```text
binary data            text
    ↓                    ↓
byte abstraction     character abstraction
```

A **byte stream** treats the payload as bytes and does not assign text meaning to them. It is the natural fit for images, ZIP archives, PDFs, encrypted data, and other binary formats.

A **character stream** treats the payload as character data. When characters cross a file or network boundary, a **charset** defines how Java characters map to bytes:

```text
characters ── encode UTF-8 ──> bytes
bytes      ── decode UTF-8 ──> characters
```

That is why arbitrary bytes should not be interpreted as “one byte equals one character.” A Unicode character may require multiple bytes in UTF-8, and a Java `char` is one UTF-16 code unit rather than a guarantee of one complete Unicode code point.

For example:

```java
String text = "Xin chào Java ☕";
byte[] utf8 = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
String restored = new String(utf8, java.nio.charset.StandardCharsets.UTF_8);

System.out.println(text.equals(restored)); // true
```

Byte streams and character streams are related layers. A character stream commonly sits on top of a byte source or sink and adds encoding/decoding. The next chapter starts with the lower-level `InputStream`/`OutputStream` model before the module introduces `Reader`/`Writer`.

## <a id="blocking-io-boundary">The Blocking I/O Boundary</a>

When code calls an I/O operation, the requested data is not necessarily already available in RAM. The program may have to wait for a filesystem, device, or network peer.

With traditional blocking I/O, a call such as `read()` may not return immediately:

```text
call read()
    ↓
data is not ready
    ↓
calling thread waits
    ↓
data / EOF / error becomes available
    ↓
read() returns or throws
```

A **thread** is one execution flow inside a process. “Blocking” means the calling thread waits for the operation to complete. It does not mean the CPU must spin at full usage during that wait. The JVM and operating system can suspend the thread while other work uses the CPU.

With **non-blocking I/O**, an operation is designed not to keep the calling thread waiting until the resource becomes ready. The call can return control earlier so the program can do other work and react when a readiness/event mechanism reports that data can be processed. This is a difference in waiting and coordination, not a promise that every operation is faster.

This creates a useful distinction between **CPU-bound work** and **I/O-bound work**. CPU-bound work spends most of its time computing on the CPU; I/O-bound work spends most of its time waiting for files, devices, sockets, or other external resources.

Not every Java I/O API is inherently blocking. NIO also provides channels and non-blocking mechanisms for appropriate workloads. These early chapters intentionally use synchronous stream/file I/O to establish the basic model first.

## <a id="resource-lifecycle">I/O Resource Lifecycles</a>

The garbage collector can reclaim Java objects after they become unreachable. A **file descriptor** is an operating-system identifier for an opened file, while **native handle** is a broader term for an operating-system-level reference to a resource such as a file, socket, or device. These resources live outside the Java heap and must be released at a predictable point in the program.

Many I/O abstractions therefore have a lifecycle:

```text
acquire/open
    ↓
read/write
    ↓
flush when needed
    ↓
close
```

A basic example:

`Path`/`Files` appear here only to create and remove a safe temporary file; learners do **not** need to understand that filesystem API yet. The later `File` and `Path/Files` chapters introduce those concepts from the beginning.

```java
java.nio.file.Path temp = java.nio.file.Files.createTempFile("io-", ".txt");

try (java.io.OutputStream out =
         new java.io.FileOutputStream(temp.toFile())) {
    out.write("Xin chào Java ☕"
        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
}

java.nio.file.Files.deleteIfExists(temp);
```

`try-with-resources` closes the stream automatically even if the body throws an exception. It is the main Java mechanism for binding a resource lifetime to a clear **code scope**, the block of code in which that resource is owned.

Not every stream owns an operating-system resource. `ByteArrayInputStream`, for example, only reads a byte array in memory and its `close()` has no resource-release effect. Even so, code receiving a general `InputStream` still needs a clear answer to **who owns the stream and who is responsible for closing it**. The later Resource Management chapter develops that ownership model in detail.

With this mental model in place, the next chapter can focus on the lowest stream abstraction: how Java reads and writes raw bytes.
