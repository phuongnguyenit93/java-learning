# try, catch and finally

## <a id="try-catch-flow">try/catch control flow</a>
Code in `try` runs until it completes normally or throws. If a thrown value matches a catch clause, control transfers to the first compatible handler; otherwise it continues propagating. After handling, execution continues after the whole construct unless the handler returns/throws.

## <a id="finally-semantics">finally execution semantics</a>
A `finally` block normally executes whether the `try` completes normally, returns, or throws, and whether a matching catch handles the failure. It is intended for cleanup that must happen regardless of outcome. Process termination or fatal VM conditions can prevent ordinary finally execution, so it is not an external durability guarantee.

## <a id="return-finally">return/throw interactions with finally</a>
A `finally` block executes after a return value has been determined but before control actually leaves. If `finally` itself returns or throws, it can replace the pending return or exception and hide the original outcome.

```java
try {
    return 1;
} finally {
    return 2; // hides the original return; avoid this
}
```

Never use `return`/normal-flow `throw` in `finally` merely to simplify control flow.

## <a id="multi-catch">Multi-catch and alternatives</a>
Multi-catch (`catch (IOException | SQLException e)`) is useful when unrelated exception types require the same handling. Alternatives in one multi-catch cannot be related by subclassing because the broader type would already cover the narrower one. Keep handling common only when the recovery/translation semantics are genuinely identical.
