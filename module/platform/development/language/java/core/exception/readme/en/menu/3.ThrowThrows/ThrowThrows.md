# throw and throws

The keywords are related but serve different roles:

```text
throw
→ at runtime, throw one Throwable instance

throws
→ in a method declaration, advertise a possible checked failure
```

## <a id="throw-statement">throw</a>

`throw` immediately transfers control away from the current path:

```java
if (amount < 0) {
    throw new IllegalArgumentException("amount must be >= 0");
}
```

Execution does not continue with the next statement in that block. The runtime searches for a matching `catch`; otherwise the exception propagates upward.

## <a id="throws-clause">throws</a>

`throws` belongs to the method declaration:

```java
String load(Path path) throws IOException {
    ...
}
```

It does not throw anything by itself. It declares that a checked exception may escape and become the caller's responsibility.

## <a id="precise-rethrow">Precise Rethrow</a>

Java can preserve narrower checked types when a caught value is rethrown without being reassigned:

```java
try {
    run();
} catch (IOException | SQLException ex) {
    throw ex;
}
```

The compiler can keep the specific checked types rather than forcing a broader declaration.

## <a id="override-throws-rules">throws Rules When Overriding</a>

An overriding method may not broaden the parent's checked-exception contract.

If the parent declares:

```java
void run() throws IOException;
```

the child may not replace it with `throws Exception`, because callers compiled against the parent contract are not prepared for that broader checked failure.

## <a id="checked-exception-narrowing">Narrowing Checked Exceptions</a>

An overriding method may:

- keep the same checked exception;
- declare a narrower subtype;
- remove the checked exception entirely.

Unchecked exceptions are not constrained by the same compile-time rule.

Next we follow an exception that is thrown but not handled locally.
