# Checked and Unchecked Exceptions

After understanding `Throwable`, the next question is: **which failures become part of a method contract that the compiler forces callers to acknowledge?**

Checked versus unchecked is a classification about **compile-time rules**, not about whether a failure is “serious” or “minor”.

## <a id="checked-exception">Checked Exceptions</a>

A checked exception is subject to the **catch-or-declare** rule.

If a checked exception may escape a method, the code must:

```text
catch it
or
declare it with throws
```

Example:

```java
String read(Path path) throws IOException {
    return Files.readString(path);
}
```

`Files.readString(path)` may throw `IOException`. If `read()` does not catch it, `read()` must declare it.

The caller then has to choose:

```java
try {
    String content = read(path);
} catch (IOException ex) {
    recover(ex);
}
```

or continue the contract:

```java
String load(Path path) throws IOException {
    return read(path);
}
```

### WHY CHECKED EXCEPTIONS EXIST

The idea is to move a failure possibility into the **compile-time API contract**:

```text
method has a failure callers are expected to acknowledge
        ↓
compiler forces handling or further declaration
```

That can be useful when callers can make a meaningful choice: select another file, switch to a fallback source, report input failure, or deliberately abort the operation.

The cost appears when callers cannot realistically recover. If every intermediate method merely repeats:

```java
throws SomeCheckedException
```

without adding any policy or abstraction, the checked contract can become boilerplate rather than useful information.

## <a id="unchecked-exception">Unchecked Exceptions</a>

Unchecked exceptions are not subject to the catch-or-declare rule.

The two major groups are:

```text
RuntimeException and subclasses
Error and subclasses
```

In application code, “unchecked exception” most often refers to the `RuntimeException` branch:

- `NullPointerException`;
- `IllegalArgumentException`;
- `IllegalStateException`;
- `IndexOutOfBoundsException`.

Example:

```java
void withdraw(long amount) {
    if (amount < 0) {
        throw new IllegalArgumentException("amount must be >= 0");
    }
}
```

The method is not required to declare:

```java
void withdraw(long amount) throws IllegalArgumentException
```

Declaring an unchecked exception in `throws` is legal, but the compiler does not require it.

### Unchecked does not mean unimportant

Unchecked only means:

```text
compiler
→ does not force callers to catch or declare it
```

It does not mean:

```text
runtime
→ the failure can be ignored
```

A `NullPointerException` can still fail a request or job. The difference is that every caller is not forced to express a compile-time decision.

Unchecked exceptions are often a good fit for:

- violated preconditions;
- invalid object state;
- programming errors;
- failures that nearby callers do not have a sensible recovery strategy for.

## <a id="checked-vs-unchecked-design">Choosing Checked vs Unchecked</a>

Avoid mechanical slogans such as:

```text
business exception = checked
modern Java = unchecked
```

Start from the caller's responsibility.

### DESIGN QUESTIONS

```text
Can and should the caller meaningfully handle this failure?
        ↓
Should every caller be forced to decide at compile time?
        ↓
Does the throws clause make the API clearer or merely noisier?
        ↓
Is this a recoverable operational condition,
or a violated contract/invariant?
```

The same “cannot read data” problem may lead to different policies:

```text
desktop tool reads a user-selected file
→ IOException may let the caller ask the user to choose another file

internal service calls a repository
→ the service may translate it into an application exception
  and handle it at a higher boundary
```

There is no universal choice. The exception category should serve the API's **handling policy**.

### COMPILE-TIME COMPARISON

```java
void checked() throws IOException {
    throw new IOException("disk failure");
}

void unchecked() {
    throw new IllegalStateException("invalid state");
}
```

Callers of `checked()` must catch or declare `IOException`. Callers of `unchecked()` are not forced to do so.

### REMEMBER

```text
checked
→ compiler forces the failure into the caller's decision

unchecked
→ compiler does not force that decision, but the runtime failure still exists
```

The next chapter separates two easily confused keywords: `throw` actually raises a throwable at runtime, while `throws` declares that an exception may escape a method.