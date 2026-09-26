# Suppressed Exceptions

Cleanup can fail too. When the body of a try-with-resources block throws and `close()` also throws, Java needs to preserve both failures without losing the original one.

## <a id="primary-vs-suppressed">Primary vs Suppressed</a>

If the body throws A and `close()` throws B:

```text
A
→ primary exception

B
→ suppressed on A
```

This keeps cleanup failure from hiding the failure that caused the operation to fail first.

## <a id="get-suppressed">Inspecting Suppressed Exceptions</a>

Suppressed throwables are available through:

```java
Throwable[] suppressed = ex.getSuppressed();
```

They are primarily diagnostic context rather than something ordinary business logic should depend on heavily.

## <a id="close-failure">Close Failure</a>

```text
body succeeds + close fails
→ close failure is primary

body fails + close fails
→ body failure is primary
→ close failure is suppressed
```

The next chapter moves from runtime mechanics to API design: when does a custom exception type add real meaning?
