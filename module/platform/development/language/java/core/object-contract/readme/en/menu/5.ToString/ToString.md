# toString

Not every object contract exists for collections. `toString` is mainly a **human and diagnostic contract** used by logs, debuggers, test failures, and ad-hoc textual output.

## <a id="tostring-purpose">Purpose of toString</a>

`Object.toString()` gives a class/identity-oriented default. Domain classes often override it to expose more useful state.

```java
@Override
public String toString() {
    return "UserId[value=" + value + "]";
}
```

The goal is readability for humans, not a durable serialization format.

## <a id="tostring-design">Designing a Useful Representation</a>

A good `toString` is usually:

- concise;
- reasonably stable for humans;
- focused on important fields;
- free of hidden I/O;
- cheap enough for diagnostics;
- unlikely to throw during logging/debugging.

If a format must be machine-readable with compatibility guarantees, use a dedicated serializer instead.

## <a id="tostring-sensitive-data">Sensitive Data and Logging</a>

Do not include passwords, tokens, secrets, full payment data, or other sensitive values simply because they are fields on the object.

`toString` may be invoked implicitly by logging, string concatenation, IDEs/debuggers, exceptions, or assertions. Its exposure surface is therefore broader than explicit calls alone.

The final two chapters move to object ordering contracts.
