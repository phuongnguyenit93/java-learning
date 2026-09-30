# throw, throws, and Exception Propagation

`throw` and `throws` are related to exceptions but belong to different moments:

```text
throw
→ a statement executed at runtime

throws
→ part of a method or constructor declaration
```

Mental model:

```text
throw  = "failure happens on this execution path now"
throws = "this method may let this failure escape to its caller"
```

## <a id="throw-statement">throw</a>

`throw` transfers control away from the current execution path.

```java
if (amount < 0) {
    throw new IllegalArgumentException("amount must be >= 0");
}
```

After the `throw` executes:

1. the next statement in the current block does not run;
2. Java looks for a compatible `catch`;
3. if none exists locally, the current method is left;
4. the exception propagates to the caller.

### `throw` does not require creating a new object

Normally, the expression after `throw` must be compatible with `Throwable`.

A language edge case is `throw null;`: it still compiles, but at runtime Java throws a `NullPointerException` at that `throw` statement.

Create a new throwable:

```java
throw new IOException("cannot read");
```

or rethrow an existing one:

```java
catch (IOException ex) {
    audit(ex);
    throw ex;
}
```

So the precise model is:

```text
throw <Throwable expression>
```

not “`throw` must always be followed by `new Exception(...)`”.

### Throw changes normal execution

```java
void validate(int quantity) {
    if (quantity < 0) {
        throw new IllegalArgumentException("quantity < 0");
    }

    save(quantity);
}
```

If `quantity < 0`, `save(quantity)` is never called. The exception both carries failure information and moves execution away from the success path.

## <a id="throws-clause">throws</a>

`throws` appears in a declaration:

```java
String load(Path path) throws IOException {
    return Files.readString(path);
}
```

It does **not** throw anything by itself. It declares that an exception may escape the method.

For checked exceptions, `throws` is required when the method does not catch the failure:

```java
String load(Path path) throws IOException {
    return Files.readString(path);
}
```

For unchecked exceptions, a declaration is legal but not required:

```java
void validate(String value) throws IllegalArgumentException {
    if (value.isBlank()) {
        throw new IllegalArgumentException("blank");
    }
}
```

Usually, do not list every imaginable `RuntimeException`. Declare or document unchecked failures when doing so materially clarifies the API contract.

### Multiple exceptions in throws

```java
void importData(Path path) throws IOException, ParseException {
    ...
}
```

The list should communicate useful failure categories. A broad `throws Exception` often loses information:

```text
caller knows "something can fail"
but
does not know which failure categories need which policies
```

When a method does not handle an exception, the failure does not disappear. Java **unwinds the call stack** while looking for a compatible handler in callers above it.

This is the bridge between `throw` and `try/catch`: `throw` creates abnormal completion at one point; propagation determines where that failure goes next.

## <a id="exception-propagation">Stack Unwinding and Propagation</a>

Reuse the module's running story:

```text
controller()
   ↓
service()
   ↓
repository()
   ↓
throw IOException
```

Example:

```java
void controller() throws IOException {
    service();
}

void service() throws IOException {
    repository();
}

void repository() throws IOException {
    throw new IOException("cannot read order");
}
```

No method catches the exception, so the flow is:

```text
repository()
→ stops at throw
→ leaves repository frame

service()
→ does not continue after repository()
→ leaves service frame

controller()
→ exception continues to controller's caller
```

Leaving those frames is **stack unwinding**.

### What happens while the stack unwinds?

When an exception is raised, Java first looks for a compatible `catch` in the active `try` constructs surrounding the failure in the current frame. If a handler catches it, propagation stops there.

If no compatible `catch` exists in the current frame, Java must leave active scopes before moving to the caller:

- an associated `finally` may still run;
- try-with-resources resources are closed according to their contract;
- after required cleanup completes, the current frame is left and handler search continues in the caller.

Example:

```java
void service() throws IOException {
    try {
        repository();
    } finally {
        System.out.println("service cleanup");
    }
}
```

`repository()` throws `IOException`, but the service `finally` executes before the exception continues upward.

### What if nobody catches it?

If an exception escapes the thread's entry point, it reaches that thread's uncaught-exception handling and the thread terminates by exceptional completion.

That does not necessarily mean “the whole JVM immediately dies”; process behavior also depends on other threads and the runtime context. The important mental model here is:

```text
no handler
→ failure continues to the thread's outer boundary
```

### WHY PROPAGATION EXISTS

A repository does not need to know:

- what a UI will display;
- which HTTP status a web boundary would produce;
- whether a batch job should retry;
- whether a higher layer will translate the exception.

The lower layer reports failure; policy belongs where enough context exists.

## <a id="catch-selection">Catch Selection by Type</a>

For a `try` with multiple handlers, Java checks them in source order and selects the first `catch` whose parameter type can accept the thrown exception.

```java
try {
    load();
} catch (FileNotFoundException ex) {
    handleMissingFile(ex);
} catch (IOException ex) {
    handleOtherIo(ex);
}
```

`FileNotFoundException` is a subtype of `IOException`, so the specific handler must come first.

This is invalid:

```java
try {
    load();
} catch (IOException ex) {
    handleIo(ex);
} catch (FileNotFoundException ex) { // unreachable
    handleMissingFile(ex);
}
```

The compiler knows every `FileNotFoundException` would already have matched the broader `IOException` handler.

### Type is a better contract than parsing messages

Avoid policies such as:

```java
catch (IOException ex) {
    if (ex.getMessage().contains("not found")) {
        ...
    }
}
```

Messages are diagnostic text and may change. If callers need different behavior, exception types or structured context are usually a stronger contract.

## <a id="exception-chaining">Exception Chaining</a>

Propagation can keep the original exception unchanged. When a new exception is created from an older one, Java can connect them through the `cause` so the original failure is not lost.

Example:

```java
Order load(long orderId) {
    try {
        return jdbcLoad(orderId);
    } catch (SQLException ex) {
        throw new RuntimeException(
                "Cannot load order " + orderId,
                ex
        );
    }
}
```

Flow:

```text
SQLException
→ original exception

RuntimeException
→ new exception

cause
→ still preserves SQLException for diagnosis
```

`RuntimeException` is only a wrapper for observing the mechanism in this example. The important point is **causal chaining**: preserve the older exception as the `cause` of the new one. Which wrapper type to use, and whether translation belongs at an abstraction boundary, are covered later in the custom-exception and boundary-design chapters.

## <a id="lost-cause-pitfall">Lost-Cause Pitfall</a>

Anti-pattern:

```java
catch (SQLException ex) {
    throw new RuntimeException("load failed");
}
```

The new exception no longer knows that the `SQLException` caused it.

Consequences:

```text
log
→ only sees the wrapper chain you kept

root stack trace
→ removed from the causal chain

debugging
→ loses where the database failure originated
```

When creating a new exception from an older one, preserve the cause:

```java
catch (SQLException ex) {
    throw new RuntimeException("load failed", ex);
}
```

This section focuses only on **not losing the cause**. The decision to rethrow the same exception or wrap it in a new type is handled in the application-boundary chapter.

### CORE MODEL RECAP

```text
throw
→ starts exceptional flow

propagation
→ moves failure upward

stack unwinding
→ leaves frames while required cleanup still participates

catch
→ stops propagation where a layer owns a policy

new wrapper + cause
→ creates a new exception without losing the root failure
```

The remaining sections are the advanced checked-exception layer: precise rethrow and `throws` rules for overriding. Learners can revisit them after the core propagation, catch-selection, and causal-chain model is solid.

## <a id="precise-rethrow">Precise Rethrow</a>

Precise rethrow handles a subtle case: code catches a broad type to perform shared logic, but the compiler can still preserve the narrower checked types that can actually reach that catch.

Example:

```java
void process() throws IOException, SQLException {
    try {
        readFileOrQueryDatabase();
    } catch (Exception ex) {
        audit(ex);
        throw ex;
    }
}
```

Assume the `try` body can only throw `IOException` or `SQLException`. If `ex` is not reassigned, the compiler can infer that rethrowing it only produces those checked types.

The method does **not** need to broaden its contract to:

```java
throws Exception
```

### WHY?

Without precise rethrow, catching a supertype for shared logic could unnecessarily widen the public contract.

Mental model:

```text
catch parameter is broad
        ↓
compiler analyzes the checked types that can actually reach it
        ↓
the parameter is not reassigned
        ↓
rethrow keeps the narrower type set
```

Multi-catch:

```java
catch (IOException | SQLException ex) {
    throw ex;
}
```

also keeps an explicit narrow type set, but that is **multi-catch**. Precise rethrow is especially interesting when the parameter is written as a broader supertype such as `Exception` and the compiler still preserves the specific checked set.

## <a id="override-throws-rules">throws Rules When Overriding</a>

Suppose the parent contract is:

```java
class Parent {
    void load() throws IOException {
    }
}
```

An override may not broaden that checked contract:

```java
class Child extends Parent {
    @Override
    void load() throws Exception { // compile error
    }
}
```

### WHY?

Code may hold the object through a `Parent` reference:

```java
Parent value = new Child();
value.load();
```

The caller is compiled against `Parent`. If `Child` could introduce a broader checked failure, callers could encounter a checked exception the parent contract never required them to prepare for.

This is part of substitutability:

```text
a subtype may promise fewer/narrower checked failures
but may not require callers to handle broader checked failures
```

Unchecked exceptions are not restricted by the same catch-or-declare rule.

## <a id="checked-exception-narrowing">Narrowing Checked Exceptions</a>

An override may:

```text
keep the same checked exception
→ throws IOException

narrow it to a subtype
→ throws FileNotFoundException

remove it entirely
→ no throws clause
```

Valid example:

```java
class Child extends Parent {
    @Override
    void load() throws FileNotFoundException {
    }
}
```

or:

```java
class InMemoryChild extends Parent {
    @Override
    void load() {
    }
}
```

The child provides an equal-or-easier checked failure contract.

### REMEMBER

```text
throw
→ runtime action

throws
→ declaration contract

override
→ may not broaden the checked contract

precise rethrow
→ catching broadly does not necessarily widen throws
```

The next chapter examines the point where propagation is handled: how `try`, `catch`, and `finally` interact across different exit paths.
