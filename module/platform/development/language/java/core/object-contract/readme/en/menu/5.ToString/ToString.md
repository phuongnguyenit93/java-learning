# toString

Not every object contract exists for collections. `toString` is mainly a **human-facing diagnostic contract** used by logs, debuggers, assertion failures, test failures, and ad-hoc textual output.

A useful `toString` helps answer: **which state of this object matters for diagnosis right now?** It should not become a serialization protocol, a security boundary, or a place for expensive business logic.

## <a id="tostring-purpose">Purpose of toString</a>

Every class inherits `toString()` from `Object` unless it overrides the method.

### DEFAULT `Object.toString()`

The default representation is roughly shaped as:

```text
fully.qualified.ClassName@hexHash
```

For example:

```text
com.example.UserId@5e2de80c
```

The suffix after `@` comes from `hashCode()` rendered in hexadecimal; it is **not a unique object identifier** and collisions are possible. The default representation can still be a rough diagnostic token, but it usually does not expose meaningful domain state.

Java also does not promise that `toString()` output remains stable across JVM executions. That is another reason not to treat it as a persistence or serialization format.

Domain classes therefore often override it:

```java
@Override
public String toString() {
    return "UserId[value=" + value + "]";
}
```

producing something closer to:

```text
UserId[value=U-100]
```

### WHY IS `toString` A CONTRACT?

The compiler does not require one exact format. The contract matters because tooling and caller code commonly expect the method to:

- return a useful description;
- avoid surprising side effects;
- be cheap and safe enough for diagnostics;
- avoid exposing data that should remain private.

### `toString` MAY BE INVOKED INDIRECTLY

You do not always call `.toString()` explicitly:

```java
System.out.println(userId);

String message = "id=" + userId;

logger.info("processing {}", userId);
```

Different APIs have different invocation details, but the important mental model is that object representation may surface in many places beyond direct calls.

## <a id="tostring-design">Designing a Useful Representation</a>

A good `toString` is usually:

- concise but informative;
- focused on fields useful for diagnosis;
- deterministic, or at least stable enough for humans to read;
- free of network/database/file I/O;
- cheap enough for logging and debugging;
- unlikely to throw while error handling is already in progress.

### REPRESENTATION IS NOT SERIALIZATION

These goals are different:

```text
toString
→ human-readable diagnostic representation

JSON / protobuf / dedicated serializer
→ machine-readable contract with schema/compatibility policy
```

Do not parse a normal domain object's `toString()` back into the object unless the type **explicitly** documents such a contract. Diagnostic formatting is free to evolve as debugging needs change.

### SHOW USEFUL STATE, NOT THE ENTIRE GRAPH

A `Book` may contain dozens of fields while a diagnostic representation only needs a few:

```java
@Override
public String toString() {
    return "Book[id=" + id + ", title=" + title + "]";
}
```

Dumping a large object graph can make logs noisy, expensive, and hard to read.

### WATCH FOR RECURSIVE OBJECT GRAPHS

If two objects refer to each other and both `toString` methods print the full nested object, representation can recurse forever or become enormous:

```text
Parent.toString()
→ prints Child
   → Child.toString()
      → prints Parent again
         → ...
```

Choose representation fields deliberately instead of blindly printing the full graph.

### RECORDS AND GENERATED REPRESENTATION

Java records generate a component-based `toString`. That is convenient for value carriers, but security and logging boundaries still apply. Generated output is not automatically safe to expose everywhere.

## <a id="tostring-sensitive-data">Sensitive Data and Logging</a>

Do not include passwords, tokens, secrets, full payment data, or other sensitive values simply because they are fields on the object.

Avoid designs such as:

```java
@Override
public String toString() {
    return "LoginRequest[user=" + user
            + ", password=" + password + "]";
}
```

### WHY IS THE EXPOSURE SURFACE LARGE?

`toString` output may surface through:

- logging;
- string concatenation;
- IDEs/debuggers;
- assertions and test failures;
- exception messages;
- nested collection/object representations.

A sensitive field added to `toString` can therefore reach production logs even when a developer never explicitly called the method at that location.

### REDACTION AND SAFE REPRESENTATION

When some identification is useful, expose a masked or reduced value:

```java
@Override
public String toString() {
    return "PaymentCard[last4=" + last4 + "]";
}
```

Detailed security policy belongs in a security-focused module, but the boundary here is clear: **`toString` is a diagnostic surface, not permission to expose all object state**.

The final two chapters move to a different object contract: **ordering** — which object comes first, and who owns that policy.
