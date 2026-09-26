# try, catch and finally

Propagation lets failures travel upward. `try/catch/finally` lets a layer decide **what to handle, what to translate, and what cleanup must still happen**.

## <a id="try-catch-flow">try/catch Flow</a>

If an exception occurs inside `try`, the remaining statements in that block are skipped and the runtime searches for a compatible `catch`.

```java
try {
    load();
    process(); // skipped if load() throws
} catch (IOException ex) {
    recover(ex);
}
```

Catch where the current layer has meaningful work to do: recover, translate, add context, or intentionally terminate a flow.

## <a id="finally-semantics">finally Semantics</a>

`finally` generally runs when leaving `try/catch`, including normal completion, `return`, or exceptional completion.

It can support manual cleanup, but try-with-resources is preferable for owned resources when available.

## <a id="return-finally">return/throw and finally</a>

A `return` or `throw` in `finally` can replace a result or exception already leaving the `try` block.

That can hide the original failure, so avoid returning from `finally` and be cautious about throwing new exceptions there.

## <a id="multi-catch">Multi-catch</a>

When several exception types need the same handling:

```java
catch (IOException | SQLException ex) {
    handle(ex);
}
```

Multi-catch removes duplication, but unrelated failure semantics should not be merged merely because their current handler body happens to match.

Next we replace manual resource cleanup with try-with-resources.
