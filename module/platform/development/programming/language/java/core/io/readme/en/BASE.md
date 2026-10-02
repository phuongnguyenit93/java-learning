# Java I/O

This module builds a mental model for **data flow and I/O resource ownership** in Java: bytes versus characters, streams versus buffers/channels, filesystem paths versus open resources, and resource lifecycle.

## Learning flow

1. the I/O mental model;
2. byte streams;
3. character streams;
4. buffered I/O;
5. legacy `File`;
6. `Path` and `Files`;
7. buffers and channels;
8. `FileChannel`;
9. resource management;
10. serialization;
11. choosing the appropriate I/O abstraction.

## Why learn this module?

I/O bugs often come from incorrect encodings, leaked resources, mismatched abstractions, or confusing a path with an already-open resource. The module prioritizes boundaries and lifecycle before optimization.

Networking and advanced asynchronous/non-blocking runtime patterns belong elsewhere; this module focuses on the Java Core I/O foundation.
