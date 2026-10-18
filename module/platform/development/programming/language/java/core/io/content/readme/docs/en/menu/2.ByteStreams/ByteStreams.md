# Byte Streams and Structured Binary Data

The opening chapter established I/O as data moving between a source and a sink. The next question is how Java represents that flow when the payload must remain binary.

## <a id="inputstream-outputstream">InputStream and OutputStream</a>

A **stream** is a sequential view of values moving from a source into the program or from the program toward a sink. It does not imply that the entire payload is already stored in RAM; code commonly processes data as it flows past.

Do not confuse a `java.io` **I/O stream** with the `java.util.stream` **Stream API**. An I/O stream reads or writes data across a source/sink boundary; `Stream<T>` is a pipeline for processing elements with operations such as `map`, `filter`, and `reduce`. They share the word “stream” but solve different problems.

`InputStream` is the abstraction for **reading bytes from a source**. `OutputStream` is the abstraction for **writing bytes to a sink**.

```text
source ──> InputStream ──> application
application ──> OutputStream ──> sink
```

Both are **abstract classes**: base classes that define shared behavior rather than one concrete source or sink. A **contract** is the behavior callers may rely on, while a **subclass** supplies an implementation for the actual source or sink:

- `FileInputStream` / `FileOutputStream`: files.
- `ByteArrayInputStream` / `ByteArrayOutputStream`: memory.
- Streams obtained from sockets or other APIs: different resources with the same byte-stream contract.

Java also exposes three process-level standard streams: `System.in` is an `InputStream` for standard input, while `System.out` and `System.err` are `PrintStream` instances for standard output/error. `PrintStream` is convenient for `print/println/printf`, but its write/print methods do not propagate `IOException`; callers use `checkError()` when that distinction matters.

This lets byte-processing code depend on `InputStream` instead of knowing whether the bytes came from a file or memory.

Here is a UTF-8 payload written and read back through byte streams:

The example uses a temporary file as a controlled source/sink. `Path` and `Files` are only example scaffolding here; the later filesystem chapters explain them directly.

```java
java.nio.file.Path temp = java.nio.file.Files.createTempFile("bytes-", ".bin");
byte[] expected = "Xin chào Java ☕"
    .getBytes(java.nio.charset.StandardCharsets.UTF_8);

try (java.io.OutputStream out =
         new java.io.FileOutputStream(temp.toFile())) {
    out.write(expected);
}

byte[] actual;
try (java.io.InputStream in =
         new java.io.FileInputStream(temp.toFile())) {
    actual = in.readAllBytes();
}

System.out.println(java.util.Arrays.equals(expected, actual)); // true
java.nio.file.Files.deleteIfExists(temp);
```

`readAllBytes()` keeps the example small, but it loads all remaining data into memory. For large or unbounded input, read incrementally in chunks; the Buffered I/O chapter later explains how buffering reduces underlying I/O calls.

## <a id="standard-console-io">Standard I/O and Console</a>

`System.in`, `System.out`, and `System.err` are the process's **standard streams**. They give programs a conventional input/output boundary without requiring the code to know whether data is connected directly to a terminal, redirected from a file, or piped to or from another process.

```text
standard input   → System.in  → InputStream
standard output  → System.out → PrintStream
standard error   → System.err → PrintStream
```

When a program runs interactively, `System.in` may receive what a user types and `System.out` may display on the screen. The shell can also redirect exactly the same program:

```text
java App < input.txt > output.txt 2> error.txt
```

The Java code still uses `System.in/out/err`, but the actual sources and sinks have changed. **Standard I/O is therefore not the same thing as terminal I/O.** This is also why library code should normally avoid closing the standard streams: their lifetime belongs to the process/launcher, and closing `System.out` can break output from code that runs later.

For a program that specifically needs an interactive terminal, Java provides `java.io.Console`:

```java
java.io.Console console = System.console();

if (console != null) {
    String name = console.readLine("Your name: ");
    char[] password = console.readPassword("Password: ");

    if (name != null) {
        console.printf("Hello %s%n", name);
    }
    if (password != null) {
        java.util.Arrays.fill(password, '\0');
    }
}
```

`Console` provides terminal-oriented operations such as `readLine`, `printf`, `reader()`, `writer()`, and `readPassword()`. `readPassword()` attempts to suppress terminal echo and returns a `char[]` so the caller can overwrite sensitive characters after use. Both `readLine(...)` and `readPassword(...)` can return `null` when the console input reaches end-of-stream, so code must not dereference their results unconditionally.

The key boundary is that `System.console()` **may return `null`**. This is common when the JVM has no suitable interactive console, such as under some IDEs, test runners, services/background processes, or when standard streams are piped or redirected. Code therefore needs a fallback or another input abstraction instead of assuming a `Console` always exists.

`System.out`/`System.err` are `PrintStream` instances, while `Console.writer()` returns a `PrintWriter`. These print APIs are convenient for text, but their `print/println/printf` methods store I/O failure in an error state instead of propagating `IOException`; use `checkError()` when the caller must observe that failure.

## <a id="read-contract">The read() Contract</a>

One of the easiest stream bugs comes from misunderstanding the return value of `InputStream.read()`.

`read()` returns an `int`, not a `byte`:

- `0..255`: the unsigned value of the byte that was read.
- `-1`: end-of-stream (EOF).

The wider `int` return type is what allows the API to represent all 256 byte values and still reserve `-1` for EOF.

```java
try (java.io.InputStream in =
         new java.io.ByteArrayInputStream(new byte[] {10, 20})) {

    int first = in.read();   // 10
    int second = in.read();  // 20
    int eof = in.read();     // -1
}
```

For `read(byte[] buffer)`, the return value is the **number of bytes actually placed into the buffer**, or `-1` when EOF is reached before any byte can be read. Code must use that count instead of assuming the entire array contains fresh data.

```java
byte[] buffer = new byte[4096];
int count;

while ((count = in.read(buffer)) != -1) {
    consume(buffer, 0, count);
}
```

`consume(...)` is an **application-defined example helper** representing whatever processing should happen to those `count` bytes. It is not a JDK method.

A zero-length bulk read may return `0`; for the usual positive-length buffer, normal processing expects a positive count or `-1`.

Two other API groups are easy to misuse:

- `available()` is **not “bytes remaining in the whole stream.”** It estimates how many bytes can currently be read or skipped without blocking and may legitimately return `0`. Do not use it to size the complete payload.
- `mark(...)` / `reset()` only work when the concrete stream supports them. Check `markSupported()` before relying on replay from a marked position; the base `InputStream` contract does not support reset by default.

## <a id="partial-read-write">Partial Reads and the Write Contract</a>

A call to `read(buffer)` does **not** promise to fill the buffer. It may return fewer bytes than the array can hold even though the stream has not reached EOF. This matters especially for networks, compressed streams, pipes, and sources that produce data incrementally.

If a format requires an exact number of bytes, the caller must loop or use a helper with the right completion semantics. `readNBytes(...)` performs repeated reads up to the requested length, but it can still return a smaller count when EOF arrives early, so code must validate that count. `DataInput.readFully(...)`, introduced below for structured binary data, instead throws `EOFException` if it cannot obtain the required bytes.

A manual exact-length loop looks like this:

```java
byte[] header = new byte[8];
int offset = 0;

while (offset < header.length) {
    int count = in.read(header, offset, header.length - offset);
    if (count == -1) {
        throw new java.io.EOFException("Incomplete header");
    }
    offset += count;
}
```

`OutputStream` has a different bulk contract. `write(byte[])` and `write(byte[], off, len)` are responsible for writing the requested range or reporting failure with an exception. Do not apply the “one write reports a smaller completed count” model from NIO `Channel` APIs to `OutputStream`; return-count-based partial writes become a direct concern when channels are introduced later.

`write(int value)` writes only the low eight bits of `value`, so array **overloads** are the normal choice for multi-byte payloads. An overload is another method with the same name but a different parameter list.

## <a id="typed-binary-io">Typed Binary I/O with DataInput/DataOutput</a>

A raw byte stream only knows that bytes are moving. Many binary formats need a stronger contract, for example:

```text
4 bytes  → record count
8 bytes  → long timestamp
1 byte   → boolean flag
N bytes  → following payload
```

`DataInput` and `DataOutput` are interfaces for reading and writing Java primitive values using a defined binary contract. `DataInputStream` wraps an `InputStream` to provide `DataInput`; `DataOutputStream` wraps an `OutputStream` to provide `DataOutput`.

```text
InputStream  → DataInputStream  → readInt/readLong/readBoolean/...
OutputStream → DataOutputStream → writeInt/writeLong/writeBoolean/...
```

This example defines a tiny format whose field order is `int version` → `long id` → `boolean active`:

```java
byte[] encoded;

try (java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
     java.io.DataOutputStream out = new java.io.DataOutputStream(bytes)) {

    out.writeInt(1);
    out.writeLong(42L);
    out.writeBoolean(true);
    encoded = bytes.toByteArray();
}

try (java.io.DataInputStream in = new java.io.DataInputStream(
        new java.io.ByteArrayInputStream(encoded))) {

    int version = in.readInt();
    long id = in.readLong();
    boolean active = in.readBoolean();
}
```

The integer methods of `DataOutput` write bytes in **big-endian** order, and `DataInput` reads the same representation. The API does not, however, know which field comes next. **Writer and reader must agree exactly on the schema/framing**: field order, field types, version, lengths, and record boundaries. If the writer emits a `long` first but the reader calls `readInt()` first, the stream is still just bytes; Java has no embedded schema that can repair the mismatch.

For fixed-size fields, `readFully(byte[])` is useful when the format requires **exactly N bytes**. It keeps reading until the array is full and throws `EOFException` if the stream ends too early:

```java
byte[] magic = new byte[4];
in.readFully(magic); // either four bytes, or EOFException
```

Methods such as `readInt()` and `readLong()` also throw `EOFException` when EOF appears in the middle of the primitive value. This differs from `InputStream.read(...)`, where callers receive the actual count and decide whether another read is required.

A variable-length binary record normally needs explicit **framing**, such as a length before its payload:

```java
byte[] payload = ...;
out.writeInt(payload.length);
out.write(payload);

int length = in.readInt();
if (length < 0 || length > 1_000_000) {
    throw new java.io.IOException("Invalid payload length: " + length);
}
byte[] payloadRead = new byte[length];
in.readFully(payloadRead);
```

Validating the length before allocating memory matters when bytes come from an untrusted source; an arbitrary length field should not be allowed to force unbounded allocation.

`writeUTF(String)` / `readUTF()` have an important special contract. They **do not use normal UTF-8 as defined by `StandardCharsets.UTF_8`**; they use the `DataInput`/`DataOutput` **modified UTF-8** format and prefix the encoded string with an unsigned 16-bit byte length. The encoded form is therefore limited to about 65,535 bytes and should not be treated as an ordinary UTF-8 interchange format. When a format requires standard UTF-8, encode explicitly with `StandardCharsets.UTF_8` and define the framing/length yourself.

Typed binary I/O is also **different from Java object serialization**. `DataInputStream`/`DataOutputStream` write primitives and bytes according to an application-defined schema; they do not automatically write class metadata or an object graph. Object serialization is introduced later as a separate topic.

## <a id="byte-stream-use-cases">When Byte Streams Fit</a>

Byte streams fit when content should remain binary or when the current layer should not make a text-encoding decision:

- images, audio, and video;
- ZIP, PDF, and other binary formats;
- encrypted or compressed payloads;
- byte-for-byte copying;
- protocols whose byte layout has its own meaning.

For a whole-stream copy:

```java
try (java.io.InputStream in = source();
     java.io.OutputStream out = destination()) {
    in.transferTo(out);
}
```

`source()` and `destination()` are **application-defined example helpers** representing code that obtains real streams from a file, socket, or another resource. They are not built-in JDK methods.

`transferTo` still operates under byte-stream semantics. It is convenient when the goal is to transfer the remaining bytes to another stream, but it does not change ownership rules: the code that owns the streams still decides when they are closed.

When the payload is actually text, operating directly on individual bytes forces application code to perform decoding and makes it easy to split a multi-byte encoded character incorrectly. The next chapter introduces `Reader`/`Writer` so charset conversion happens at an explicit boundary.
