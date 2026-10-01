# Path, Files, and the Modern Filesystem Model

After `java.io.File`, modern Java usually works with the filesystem through two separate roles: `Path` represents a **path**, while `Files` performs **I/O operations and metadata queries** on that path. This separation lets us answer two different questions: where is the resource, and what operation should be performed on it?

The examples in this chapter use a temporary directory and a UTF-8 text file:

~~~java
Path tempDir = Files.createTempDirectory("io-demo-");
Path note = tempDir.resolve("note.txt");
String text = "Hello Java I/O";
~~~

## <a id="path-model">The Path Model</a>

A `Path` models a path in a filesystem. An **absolute path** starts from a root/location that identifies the target independently of the current working directory, such as `C:\work\note.txt` or `/tmp/note.txt`. A **relative path** such as `data/note.txt` must be interpreted against a base path, often the working directory or another `Path` that code explicitly resolves it against.

The first important rule for a beginner is that **creating a Path does not access the filesystem**:

~~~java
Path path = Path.of("data", "note.txt");
~~~

The file may or may not exist. Filesystem work begins when code calls operations such as `Files.readString(path)`, `Files.exists(path)`, or `Files.createFile(path)`.

A `Path` is also more than a `String`. It understands path components:

~~~java
Path path = Path.of("data", "reports", "2026", "summary.txt");

System.out.println(path.getFileName()); // summary.txt
System.out.println(path.getParent());   // data/reports/2026 in filesystem-specific form
System.out.println(path.isAbsolute());  // false
~~~

Separators and display form depend on the filesystem. Build paths with `Path.of(...)`, `resolve(...)`, and other `Path` operations instead of manually joining strings with a separator character.

Keep the role split clear:

~~~text
Path
→ describes a path/location

Files
→ performs filesystem operations using a Path
~~~

The previous chapter introduced legacy `File`. `Path` and `Files` are generally preferred in modern Java because their responsibilities are clearer, they expose richer operations, and failures are represented by more specific exceptions.

## <a id="resolve-normalize">resolve, normalize, and relativize</a>

Applications commonly have a base directory and need paths below it. `resolve` expresses that relationship directly:

~~~java
Path root = Path.of("workspace");
Path file = root.resolve("notes").resolve("today.txt");
~~~

If the argument passed to `resolve` is absolute, the result is that absolute path. This matters when a path fragment comes from configuration or input.

`normalize()` simplifies path syntax, removing `.` elements and reducible `name/..` pairs:

~~~java
Path raw = Path.of("data", ".", "reports", "..", "note.txt");
Path normalized = raw.normalize();

System.out.println(normalized); // data/note.txt
~~~

`normalize()` **does not access the filesystem** and does not resolve symbolic links. A symbolic link (often called a symlink) is a filesystem entry that points to another path/target instead of containing that target's data itself. That is why `normalize()` differs from `toRealPath()`: `toRealPath()` accesses the filesystem, requires the path to exist, and resolves it according to filesystem/link behavior.

With symbolic links, a path containing `..` can have runtime meaning that differs from its purely syntactic normalized form. Do not treat `normalize()` as proof that two paths necessarily identify the same file.

`relativize()` answers the reverse question: “how do I get from A to B using a relative path?”:

~~~java
Path base = Path.of("workspace", "docs");
Path target = Path.of("workspace", "images", "logo.png");

Path relative = base.relativize(target);
System.out.println(relative);
~~~

The paths must be compatible, for example both absolute or both relative and from a compatible filesystem. `relativize()` is useful for stored relative links or shorter display paths, but it is still a path operation; it does not read or write file content.

## <a id="files-operations">Files Operations</a>

Once a `Path` exists, the `Files` utility class provides common filesystem operations. For small files with known bounds, modern Java provides convenient whole-content read/write methods:

~~~java
Path tempDir = Files.createTempDirectory("io-demo-");
Path note = tempDir.resolve("note.txt");

Files.writeString(
        note,
        "Hello Java I/O",
        StandardCharsets.UTF_8
);

String loaded = Files.readString(note, StandardCharsets.UTF_8);
System.out.println(loaded);
~~~

The explicit `StandardCharsets.UTF_8` makes the byte-to-character conversion rule visible.

Common operation groups include:

| Need | Typical API |
| --- | --- |
| Create directories | `Files.createDirectory`, `Files.createDirectories` |
| Create a file | `Files.createFile` |
| Read/write a small file | `readString`, `writeString`, `readAllBytes`, `write` |
| Open a stream/reader/writer | `newInputStream`, `newOutputStream`, `newBufferedReader`, `newBufferedWriter` |
| Copy | `copy` |
| Move/rename | `move` |
| Delete | `delete`, `deleteIfExists` |

`createDirectory` creates one directory and requires its parent to exist. `createDirectories` creates missing parents as needed:

~~~java
Path reports = tempDir.resolve("a").resolve("b").resolve("reports");
Files.createDirectories(reports);
~~~

Copy and move behavior should be explicit when options matter:

~~~java
Path copy = tempDir.resolve("note-copy.txt");
Files.copy(note, copy, StandardCopyOption.REPLACE_EXISTING);

Path moved = tempDir.resolve("archive.txt");
Files.move(copy, moved, StandardCopyOption.REPLACE_EXISTING);
~~~

`StandardCopyOption.ATOMIC_MOVE` can request an atomic move: observers should not see an intermediate state where only part of the move has happened. Not every filesystem supports this capability. If atomicity is a real requirement, handle `AtomicMoveNotSupportedException` instead of assuming the option always succeeds.

Several operation semantics are easy to misread:

- `Files.copy(source, target)` follows a source symbolic link by default and copies the link target. With `LinkOption.NOFOLLOW_LINKS`, the link itself is copied instead.
- `Files.move(...)` moves the symbolic link itself when the source is a symlink; it does not move the target the link points to.
- `Files.copy(...)` on a directory creates the corresponding target directory; it does **not** recursively copy the directory tree. Recursive copy requires tree traversal such as `walkFileTree`.
- `Files.delete(...)` is not a recursive tree delete; deleting a non-empty directory can fail with `DirectoryNotEmptyException`.
- With `ATOMIC_MOVE`, the other move options are ignored. If the target already exists, replace-versus-fail behavior is implementation-specific, so portable code must not assume `REPLACE_EXISTING` is guaranteed in an atomic move.

`Files` also has overloads that accept `StandardOpenOption`:

~~~java
Files.writeString(
        note,
        System.lineSeparator() + "next line",
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND
);
~~~

A compact mental model for common options:

| Option | Meaning |
| --- | --- |
| `READ` | Open for reading |
| `WRITE` | Open for writing |
| `CREATE` | Create the file if it does not exist |
| `CREATE_NEW` | Create only if absent; fail if it already exists |
| `APPEND` | Write at the end of the file |
| `TRUNCATE_EXISTING` | When opened for writing, truncate an existing file to length 0 |

These options describe the **file-open contract**, not just syntax. `CREATE_NEW`, for example, expresses “create-or-fail,” while `APPEND` expresses “add to the end rather than overwrite from the beginning.”

For large content, `readAllBytes` and `readString` are poor defaults because they load the whole file into memory. Streams, readers/writers, or channels let the program process data incrementally. The final chapter will bring these choices together.

## <a id="temporary-files">Temporary Files and Directories</a>

Applications often need short-lived storage for intermediate data: an upload being processed, extracted files, transform output, a short-lived cache, or test fixtures. Manually choosing a name such as `tmp.txt` can collide across threads or processes and also forces the application to invent its own temporary-directory policy. `Files.createTempFile(...)` and `Files.createTempDirectory(...)` solve that problem by **actually creating** a new file or directory whose generated name is made suitably unique by the filesystem provider for that creation context.

```java
Path tempFile = Files.createTempFile("report-", ".txt");
Path tempDir = Files.createTempDirectory("work-");
```

Overloads without a parent use the environment's **default temporary-file directory**. When the application needs a controlled location, pass a directory explicitly:

```java
Path workspace = Path.of("work");
Files.createDirectories(workspace);

Path tempFile = Files.createTempFile(workspace, "upload-", ".bin");
Path tempDir = Files.createTempDirectory(workspace, "extract-");
```

Constructing a `Path` only creates a path value. By contrast, `createTempFile` and `createTempDirectory` are **filesystem operations**, and the resource exists when the method returns successfully.

A common misconception is that “temporary” means the JVM will automatically clean the resource up. **There is no such general guarantee.** Try-with-resources closes open streams/channels; it does not delete the file or directory that those handles refer to. Code that creates a temporary resource also needs an ownership and cleanup policy:

```java
Path temp = Files.createTempFile("job-", ".dat");

try {
    Files.writeString(temp, "intermediate data", StandardCharsets.UTF_8);
    process(temp); // application-defined helper
} finally {
    Files.deleteIfExists(temp);
}
```

For a temporary directory, its contents must be removed before the directory itself can be deleted; `Files.delete(tempDir)` is not recursive. That is one direct reason tree walking becomes relevant to cleanup of more complex temporary workspaces.

Some file-opening APIs accept `StandardOpenOption.DELETE_ON_CLOSE`:

```java
Path temp = Files.createTempFile("session-", ".bin");

try (SeekableByteChannel channel = Files.newByteChannel(
        temp,
        StandardOpenOption.WRITE,
        StandardOpenOption.DELETE_ON_CLOSE)) {
    channel.write(ByteBuffer.wrap(new byte[] {1, 2, 3}));
}
```

This option asks the implementation to make a **best-effort deletion** when the resource is closed. It is useful for temporary files whose lifetime is tightly coupled to one open handle, but it should not be the only cleanup strategy for every environment: provider/platform behavior can differ, and abnormal termination is not a transactional deletion guarantee. When cleanup is an application requirement, explicit ownership plus a clear `finally`/cleanup workflow is the safer mental model.

## <a id="file-attributes">File Attributes and Metadata</a>

File content is the data inside the file. **Metadata** describes the file: size, timestamps, file type, permissions, and other filesystem-provided properties.

Simple queries include:

~~~java
System.out.println(Files.exists(note));
System.out.println(Files.isRegularFile(note));
System.out.println(Files.isDirectory(note));
System.out.println(Files.size(note));
System.out.println(Files.getLastModifiedTime(note));
~~~

`Files.exists(path)` and `Files.notExists(path)` are **not exact logical complements**. If the filesystem cannot determine the state, both can return `false`:

~~~text
exists == true
→ existence was confirmed at the time of the check

notExists == true
→ non-existence was confirmed at the time of the check

both false
→ the state could not be determined
~~~

If the real question is whether two `Path` values identify the same file, do not infer that only from path strings or `normalize()`. `Files.isSameFile(a, b)` handles filesystem identity and cases such as symbolic links more directly. One important caveat is that when the two `Path` values are already equal, `isSameFile` returns `true` without checking whether the file exists, so **it is not an existence check**.

When several attributes are needed together, `readAttributes` expresses that intent directly:

~~~java
BasicFileAttributes attrs =
        Files.readAttributes(note, BasicFileAttributes.class);

System.out.println(attrs.size());
System.out.println(attrs.creationTime());
System.out.println(attrs.lastModifiedTime());
System.out.println(attrs.isRegularFile());
~~~

`BasicFileAttributes` is a relatively portable baseline. Some filesystems support specialized views such as `PosixFileAttributes` or DOS attributes. Code using those views must accept that another filesystem may not support them.

Symbolic links also affect metadata. Many operations follow a link to its target by default. When code needs information about the link entry itself, applicable APIs accept `LinkOption.NOFOLLOW_LINKS`:

~~~java
boolean followsTarget = Files.exists(note);
boolean pathEntryExists =
        Files.exists(note, LinkOption.NOFOLLOW_LINKS);
~~~

A common design mistake is:

~~~text
check exists
→ assume the state remains unchanged
→ perform the real operation later
~~~

The filesystem can change between the check and the operation because of another thread or process. Let the real operation determine success or failure and handle its exception instead of treating an earlier check as a guarantee.

## <a id="filesystem-provider-model">FileSystem, FileSystemProvider, and FileStore</a>

A `Path` does not exist by itself. **Every `Path` belongs to a particular `FileSystem`.** A `FileSystem` describes the namespace and path rules in which that path lives: available roots, separator rules, backing stores, and the capabilities exposed by the provider underneath it.

Keep this mental model:

~~~text
Path
→ belongs to a FileSystem
→ the FileSystem is implemented/provided by a FileSystemProvider
→ data is backed by one or more FileStore objects
~~~

In most ordinary desktop/server programs, `Path.of(...)` uses the operating system's **default filesystem**. NIO.2 is broader than that, however: Java can work with alternate provider-backed filesystems, for example ZIP/JAR filesystems. That is one reason `Path` is a richer abstraction than “a Windows/Linux path string.”

~~~java
Path path = Path.of("data", "note.txt");
FileSystem fs = path.getFileSystem();

System.out.println(fs.provider().getScheme());
System.out.println(fs.getSeparator());
~~~

`FileSystemProvider` is the implementation layer behind filesystem operations. Application code normally **does not call a provider directly**; it uses `Path` and `Files`, and the provider performs the operation according to that filesystem's capabilities. This gives one common explanation for several portability boundaries in this chapter:

~~~text
ATOMIC_MOVE may be unsupported
POSIX permission views may not exist
WatchService delivery can differ
symbolic-link behavior can differ
→ because capability/semantics depend on the filesystem + provider
~~~

`FileStore` represents the backing storage/filesystem volume containing a path. It can expose information such as capacity and supported attribute views:

~~~java
FileStore store = Files.getFileStore(path);

long total = store.getTotalSpace();
long usable = store.getUsableSpace();

boolean posix =
        store.supportsFileAttributeView("posix");
~~~

Capacity values are snapshots at query time, not quota or transaction guarantees. Likewise, a `FileStore` reporting support for an attribute view means that capability exists at the store/provider level; a specific operation can still fail because of permissions, file state, or races.

One important consequence is that **not every `Path` from every provider can be converted to `java.io.File`**. `Path.toFile()` is supported only by the default provider; another provider can throw `UnsupportedOperationException`. Modern code should therefore keep values as `Path` instead of converting to `File` merely by habit.

Paths from incompatible providers/filesystems also should not be mixed casually. Operations such as `resolve`, `relativize`, copy, and move have filesystem/provider boundaries of their own. Reusable code should treat **filesystem identity** as part of the context instead of assuming every path is the same kind of path.

Beginners do not need to implement a custom `FileSystemProvider` here. The goal is architectural understanding: a `Path` has an owning `FileSystem`, `Files` dispatches operations through the provider, and `FileStore` describes backing storage/capabilities. That mental model ties together the portability caveats throughout this chapter.

## <a id="file-permissions-ownership">Attribute Views, Permissions, and Ownership</a>

`BasicFileAttributes` provides a relatively portable baseline, but filesystems expose additional groups of metadata. NIO.2 models these groups as **file attribute views**: a view describes which family of metadata a provider can read or write.

```text
BasicFileAttributeView
→ size, timestamps, file type, fileKey...

PosixFileAttributeView
→ owner, group, rwx permissions on POSIX-style filesystems

DosFileAttributeView
→ readonly, hidden, archive, system on providers with DOS attributes

FileOwnerAttributeView
→ owner represented as a UserPrincipal
```

Do not infer support only from the operating-system name. Ask the filesystem/provider that actually stores the file:

```java
FileStore store = Files.getFileStore(note);

boolean posixSupported =
        store.supportsFileAttributeView(PosixFileAttributeView.class);
boolean dosSupported =
        store.supportsFileAttributeView(DosFileAttributeView.class);
```

When POSIX attributes are supported, code can read a set of `PosixFilePermission` values:

```java
if (Files.getFileStore(note)
        .supportsFileAttributeView(PosixFileAttributeView.class)) {

    Set<PosixFilePermission> permissions =
            Files.getPosixFilePermissions(note);

    System.out.println(permissions);
}
```

POSIX permissions model `READ`, `WRITE`, and `EXECUTE` access for **owner / group / others**. When an application really needs to change them, `Files.setPosixFilePermissions(...)` writes a new permission set, but the operation can still fail because the provider does not support POSIX attributes or because the process lacks the required authority. Permissions are therefore a filesystem capability plus an operating-system authorization question, not merely a Java enum.

Ownership is represented by a `UserPrincipal`:

```java
UserPrincipal owner = Files.getOwner(note);
System.out.println(owner.getName());
```

`Files.setOwner(...)` or a `FileOwnerAttributeView` can change the owner when the provider supports that operation and the operating system permits it. Changing ownership is commonly privileged, so code must not assume that the current process is always allowed to do it.

Symbolic links remain an important boundary. Attribute APIs commonly follow the target by default. When code needs metadata for **the link entry itself**, use an API/view that accepts `LinkOption.NOFOLLOW_LINKS`, for example:

```java
PosixFileAttributeView linkView = Files.getFileAttributeView(
        linkPath,
        PosixFileAttributeView.class,
        LinkOption.NOFOLLOW_LINKS);
```

The view may be `null` when the provider does not support that attribute family. Portable code should provide a fallback or state its required capability clearly instead of assuming every filesystem has identical POSIX, DOS, ACL, or ownership semantics.

## <a id="directory-stream-walk">Directory Listing and Walking</a>

Reading a directory is also I/O. For one directory level, `Files.list` returns a `Stream<Path>`:

This `Stream<Path>` is a **`java.util.stream.Stream`**, meaning the Stream API for processing `Path` elements; it is not an `InputStream` or `OutputStream`. The important twist is that this Stream API pipeline was created by an I/O operation and keeps a directory resource underneath it, so it still has a close lifecycle.

~~~java
try (Stream<Path> entries = Files.list(tempDir)) {
    entries.forEach(System.out::println);
}
~~~

The stream holds a filesystem resource while it is being traversed, so it must be closed. That is why it belongs in a try-with-resources statement.

For recursive traversal:

~~~java
try (Stream<Path> paths = Files.walk(tempDir)) {
    paths
            .filter(Files::isRegularFile)
            .forEach(System.out::println);
}
~~~

`Files.walk` traverses a directory tree to the requested depth. By default it does not follow symbolic links. If `FileVisitOption.FOLLOW_LINKS` is enabled, link cycles can lead to errors such as `FileSystemLoopException`.

When code needs per-file/per-directory callbacks, branch-specific failure handling, or explicit control over whether traversal continues after an error, `Files.walkFileTree` with a `FileVisitor` is a better fit.

`DirectoryStream<Path>` is another way to iterate directory entries:

~~~java
try (DirectoryStream<Path> stream =
             Files.newDirectoryStream(tempDir, "*.txt")) {
    for (Path entry : stream) {
        System.out.println(entry);
    }
}
~~~

`DirectoryStream` must also be closed. For very large directories or trees, avoid collecting every `Path` into a `List` if entries can be processed incrementally.

## <a id="watch-service-boundary">Watching Filesystem Changes with WatchService</a>

Sometimes an application needs more than a snapshot of a directory: it needs to know whether entries are **created, modified, or deleted after registration**. `WatchService` is the NIO.2 abstraction for receiving this kind of filesystem change notification.

Mental model:

```text
directory
   ↓ register
WatchService
   ↓ filesystem/provider reports a change
WatchKey becomes signalled
   ↓ pollEvents()
WatchEvent: create / modify / delete / overflow
   ↓ processing complete
reset WatchKey
```

Create a watcher and register one directory:

```java
try (WatchService watcher = tempDir.getFileSystem().newWatchService()) {
    tempDir.register(
            watcher,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_DELETE);

    WatchKey key = watcher.take(); // block until a key is signalled

    for (WatchEvent<?> event : key.pollEvents()) {
        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
            System.out.println("One or more events may have been lost");
            continue;
        }

        Path relative = (Path) event.context();
        Path changed = tempDir.resolve(relative);
        System.out.println(event.kind().name() + ": " + changed);
    }

    boolean stillValid = key.reset();
    if (!stillValid) {
        System.out.println("Directory registration is no longer valid");
    }
}
```

`take()` **blocks** until a `WatchKey` is available. `poll()` returns immediately and yields `null` when no key is ready; the `poll(timeout, unit)` overload provides a bounded wait. These methods express different coordination policies rather than different event accuracy.

A `WatchKey` represents a registration that has been signalled. After consuming its events with `pollEvents()`, code must call `reset()` so the key can return to the waiting state and be signalled again. If `reset()` returns `false`, the registration is no longer valid, for example because the directory was deleted or the key was cancelled.

For directory events, `WatchEvent.context()` is normally a path **relative to the registered directory**, so code resolves it against that directory when it needs a full path. A watcher is also **not recursive by default**: registering `root` does not automatically watch all current or future subdirectories; recursive designs must manage registrations for the relevant directories themselves.

`OVERFLOW` is a crucial signal: the provider reports that events may have been lost or discarded. When correctness requires the final filesystem state to be exact, the application should **rescan the filesystem** instead of treating the received event list as a complete history.

`WatchService` is an abstraction over the filesystem/provider, so observation details are **not completely portable**. Events may be coalesced, the number of `ENTRY_MODIFY` notifications can vary, a rename may appear as delete + create, latency can differ, and remote/network filesystems may behave differently. Treat a watcher as a notification that filesystem state changed and should be examined, not as an absolute transaction log.

`WatchService` is a resource and must be closed. Closing it releases registrations/provider resources; a thread waiting on the watcher can receive `ClosedWatchServiceException`. Its ownership should therefore be explicit just like other I/O resources.

At this point `Path` gives us an address and `Files` gives us operations. The next chapter changes the model: NIO separates **the data staging area** (`Buffer`) from **the conduit that moves data** (`Channel`).
