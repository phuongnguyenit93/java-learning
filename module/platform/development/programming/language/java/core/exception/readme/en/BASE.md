# Exceptions

This module treats exceptions as part of a **failure contract and control flow**, not merely `try/catch` syntax. The goal is to decide which failures should be thrown, propagated, translated, handled, or cleaned up at each boundary.

## Learning flow

1. why exceptions exist;
2. the `Throwable` model;
3. checked and unchecked exception contracts;
4. throwing and propagation;
5. handling and cleanup;
6. resource-safe failure;
7. custom exceptions and context;
8. exception-boundary design;
9. a final failure model.

## Mental model to retain

Catching earlier is not automatically better design. A layer should handle a failure only when it has enough context to recover, translate, or choose a policy. Resource cleanup must remain correct whether the operation succeeds or fails.

This module focuses on Java exception semantics; logging, HTTP error mapping, and framework-specific exception handling belong to higher-level modules.
