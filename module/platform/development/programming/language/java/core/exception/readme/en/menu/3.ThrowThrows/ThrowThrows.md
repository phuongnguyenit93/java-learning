# throw and throws

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

The expression after `throw` must be compatible with `Throwable`.

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

The next chapter follows an exception after it is thrown: how Java unwinds the call stack and chooses a handler.