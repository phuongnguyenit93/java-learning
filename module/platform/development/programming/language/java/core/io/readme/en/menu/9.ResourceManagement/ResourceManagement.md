# Resource Lifetime and Failure Safety

An ordinary `byte[]` or `String` lives in memory managed by the JVM. Streams, channels, directory streams, and file locks are different: they often represent an **external resource** such as a file descriptor (an operating-system identifier for an open I/O resource such as a file, socket, or pipe) or another native handle supplied by the platform.

That means I/O lifetime cannot rely on “when references disappear, GC will take care of it.” GC manages Java memory; application code still needs deterministic release of I/O resources.

~~~text
open/acquire
→ use
→ close/release

this must work after:
→ success
→ early return
→ exception
~~~

## <a id="closeable-lifecycle">Closeable and AutoCloseable</a>

Java models “can be closed” with `AutoCloseable`:

~~~java
public interface AutoCloseable {
    void close() throws Exception;
}
~~~

`Closeable` is the common I/O specialization. It extends `AutoCloseable` and its `close()` contract uses `IOException`.

Familiar types such as `InputStream`, `OutputStream`, `Reader`, `Writer`, `FileChannel`, `DirectoryStream`, and `FileLock` all have a lifetime that matters according to their API contract.

The useful mental model is:

~~~text
new/open
→ an external resource is acquired

Java object is still usable
→ the external resource may still be held

close
→ use ends and the resource is released according to its contract
~~~

After close, do not continue using the resource unless the API explicitly says an operation remains valid. Most operations on closed streams/channels fail.

`Closeable.close()` is specified so repeated close calls have no further effect. That is not a general requirement for every `AutoCloseable` implementation. A clearer design still has **one owner** responsible for closing the resource once in the normal lifecycle.

## <a id="resource-ownership">Who Owns and Closes the Resource?</a>

Try-with-resources answers **how to close**. Before that, code must decide **who is responsible for closing**.

A useful design convention is:

~~~text
code creates/opens the resource
→ it owns the resource by default
→ it closes the resource

code only receives a caller-provided resource
→ do not guess ownership
→ the contract must state who closes it
~~~

For example, a method that opens its own file:

~~~java
static long countBytes(Path path) throws IOException {
    try (InputStream in = Files.newInputStream(path)) {
        long count = 0;
        byte[] buffer = new byte[8192];
        int read;

        while ((read = in.read(buffer)) != -1) {
            count += read;
        }

        return count;
    }
}
~~~

The method creates the `InputStream`, so ownership is clear and the method closes it.

Now consider a caller-provided stream:

~~~java
static void writeMessage(
        OutputStream out,
        String message
) throws IOException {
    out.write(message.getBytes(StandardCharsets.UTF_8));
}
~~~

`out` came from the caller. Closing it unexpectedly could prevent the caller from writing more to a file, socket, or response. An API should make one of these contracts clear:

~~~text
borrowed resource
→ method uses it but does not close it

owned/transferred resource
→ method receives ownership and will close it
~~~

Ownership also applies to less obvious APIs. `Files.list`, `Files.walk`, `Files.find`, and `Files.lines` return `java.util.stream.Stream` values that are backed by I/O resources; consuming code must arrange for `close()`, usually with try-with-resources. Being a Stream API pipeline does not remove the resource lifecycle.

An object becoming eligible for GC does not mean its external resource was released at the right time. In a long-running server, leaked file descriptors can accumulate until the process can no longer open files or sockets.

## <a id="try-with-resources-io">Try-with-resources for I/O</a>

Manually placing `close()` after work is easy to get wrong:

~~~java
InputStream in = Files.newInputStream(path);
byte[] data = in.readAllBytes();
in.close();
~~~

If `readAllBytes()` throws, the final line is skipped. Try-with-resources binds cleanup to the language construct:

~~~java
try (InputStream in = Files.newInputStream(path)) {
    byte[] data = in.readAllBytes();
    System.out.println(data.length);
}
~~~

When execution leaves the block, Java calls `close()` whether the body completed normally or threw.

Several resources can be declared together:

~~~java
try (
        InputStream in = Files.newInputStream(source);
        OutputStream out = Files.newOutputStream(target)
) {
    in.transferTo(out);
}
~~~

They close in reverse declaration order: `out` first, then `in`. That ordering is useful when a later resource depends on an earlier one.

If the body throws and closing also throws, Java keeps the primary failure and records close failures as suppressed exceptions. That fact is useful when reading I/O failures; the full exception/suppression model belongs to the exception learning module rather than this I/O chapter.

Since Java 9, an existing final or effectively-final variable can also appear in the resource header:

~~~java
InputStream in = Files.newInputStream(path);

try (in) {
    System.out.println(in.read());
}
~~~

For I/O, try-with-resources should be the default when ownership fits a lexical scope.

## <a id="close-wrapper-chain">Closing Wrapper Chains</a>

Earlier chapters used decorator-style wrappers:

~~~text
FileInputStream
→ InputStreamReader
→ BufferedReader
~~~

Standard I/O wrappers generally **propagate `close()` to their delegate**, the lower-level object to which the wrapper forwards read/write operations, according to their API contract. That close-propagation rule is separate from application-level ownership: code still has to decide whether it was responsible for closing the wrapped resource in the first place.

~~~java
try (BufferedReader reader = Files.newBufferedReader(
        path,
        StandardCharsets.UTF_8
)) {
    System.out.println(reader.readLine());
}
~~~

For a manually built chain:

~~~java
InputStream file = Files.newInputStream(path);
Reader decoder =
        new InputStreamReader(file, StandardCharsets.UTF_8);
BufferedReader buffered = new BufferedReader(decoder);
~~~

closing `buffered` closes `decoder` and then the lower delegate according to the standard wrapper contracts. When application-level ownership says this scope owns the whole chain, the outermost wrapper is usually the resource that needs to appear in try-with-resources.

Output wrappers add another reason to close correctly: they may still hold buffered output. Closing standard writer/output wrappers performs the required flush behavior before closing their delegate:

~~~java
try (BufferedWriter writer = Files.newBufferedWriter(
        path,
        StandardCharsets.UTF_8
)) {
    writer.write("hello");
}
~~~

Do not substitute “the process is about to exit” for a proper `close()` lifecycle.

The key caveat appears when a wrapper surrounds a resource the method **does not own**. Closing the wrapper will usually close the caller's delegate too. That is not a wrapper bug; it means the ownership contract was unclear.

With resource lifetime under control, the next chapter examines an I/O feature with a much larger boundary: Java native object serialization. Turning an object graph into bytes introduces both compatibility and security concerns.
