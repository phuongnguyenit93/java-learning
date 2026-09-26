# Checked and Unchecked Exceptions

The next question is: **which failures become part of a method's compile-time contract?**

## <a id="checked-exception">Checked Exceptions</a>

For a checked exception that may escape a method, the compiler requires the method to either:

```text
catch it
or
declare it with throws
```

```java
String read(Path path) throws IOException {
    return Files.readString(path);
}
```

`IOException` therefore becomes visible in the method contract and callers must decide whether to handle, translate, or propagate it.

Checked exceptions are useful when callers can reasonably be expected to acknowledge a recoverable/operational failure. Overusing them for failures callers cannot meaningfully handle can make APIs noisy.

## <a id="unchecked-exception">Unchecked Exceptions</a>

Exceptions under `RuntimeException` are unchecked. The compiler does not require callers to catch or declare them.

Common examples include `NullPointerException`, `IllegalArgumentException`, `IllegalStateException`, and `IndexOutOfBoundsException`.

Unchecked does **not** mean unimportant; it only means the checked-exception compile-time rules do not apply.

## <a id="checked-vs-unchecked-design">Choosing Checked vs Unchecked</a>

Avoid simplistic rules such as “business exception = checked” or “modern Java = unchecked”. Ask instead:

```text
Can and should the caller meaningfully handle this failure?
        ↓
Should this possibility be explicit in the compile-time contract?
        ↓
Does forcing catch/declare make the API clearer or merely noisier?
```

The next chapter separates the two keywords that often get confused: `throw` and `throws`.
