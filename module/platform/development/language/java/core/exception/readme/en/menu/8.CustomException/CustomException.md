# Custom Exceptions

Not every failure needs a new exception class. A custom exception is valuable when the new type **adds meaning**, creates a useful abstraction boundary, or gives callers a meaningful handling category.

The question is not:

```text
"Can we create our own exception?"
```

It is:

```text
"What does the caller gain from this separate type?"
```

## <a id="custom-exception-purpose">When Custom Exceptions Help</a>

A custom exception can be useful when it:

- expresses a domain/application failure in the right vocabulary;
- hides lower-level implementation details;
- gives callers a meaningful category to catch;
- carries structured context that plain message text cannot represent well;
- creates a more stable contract than the underlying library/infrastructure exceptions.

Suppose a repository may later switch from files to a database. The service may not want callers coupled to `IOException` or `SQLException`.

```java
try {
    return repository.load(orderId);
} catch (IOException ex) {
    throw new OrderLoadException(orderId, ex);
}
```

The caller sees:

```text
OrderLoadException
```

rather than knowing that the repository currently uses files.

### When is a custom type unnecessary?

Avoid creating:

```java
class InvalidArgumentForOrderException extends RuntimeException {
}
```

merely to rename `IllegalArgumentException` when the new type adds no:

- domain meaning;
- structured context;
- handling policy;
- abstraction boundary.

A large set of classes that differ only by name makes an API harder to understand without adding expressiveness.

### Checked or unchecked custom exception?

Both are valid.

Unchecked:

```java
class OrderLoadException extends RuntimeException {
}
```

Checked:

```java
class OrderLoadException extends Exception {
}
```

The choice returns to the checked/unchecked design question:

```text
Should the compiler force every caller to catch or declare this failure?
```

Do not choose purely from slogans such as “custom exceptions should extend RuntimeException” or “business exceptions should be checked”.

## <a id="exception-context">Preserving Context and Cause</a>

A useful custom exception often preserves its cause:

```java
public final class OrderLoadException extends RuntimeException {

    private final long orderId;

    public OrderLoadException(long orderId, Throwable cause) {
        super("Cannot load order " + orderId, cause);
        this.orderId = orderId;
    }

    public long getOrderId() {
        return orderId;
    }
}
```

Usage:

```java
catch (IOException ex) {
    throw new OrderLoadException(orderId, ex);
}
```

The exception now carries:

```text
type
→ OrderLoadException

structured context
→ orderId

message
→ human-facing diagnostic context

cause
→ original IOException
```

### A message is not structured context

If code needs the `orderId`, do not force callers to parse:

```text
"Cannot load order 42"
```

from a message.

A field/accessor is a stronger contract:

```java
ex.getOrderId()
```

Messages serve people and diagnostics; structured fields serve programmatic contracts.

### Keep secrets out of exceptions

Exceptions frequently reach logs, tracing, and monitoring.

Avoid embedding:

- passwords;
- access tokens;
- secret keys;
- full credentials;
- unnecessary sensitive payloads.

“Add useful context” does not mean “copy the entire request into the exception message”.

## <a id="exception-hierarchy-design">Designing Exception Hierarchies</a>

A small hierarchy can let callers handle at different levels:

```text
OrderException
├── OrderNotFoundException
└── OrderValidationException
```

A caller that wants one policy for all order failures can catch:

```java
catch (OrderException ex) {
    handleOrderFailure(ex);
}
```

A caller that needs a specific policy can catch:

```java
catch (OrderNotFoundException ex) {
    showNotFound(ex);
}
```

### Design the hierarchy around handling policy

Do not mirror every internal implementation class:

```text
JdbcOrderTableReadException
JdbcOrderColumnMappingException
JdbcOrderConnectionAcquireException
...
```

if callers only have one meaningful policy:

```text
"the order could not be loaded"
```

A good hierarchy asks:

```text
Does the caller need to distinguish these failures
because it will act differently?
```

If not, separate types may be over-design.

### Constructors should support the causal chain

A base exception often needs only the constructors its contract actually uses:

```java
OrderException(String message) {
    super(message);
}

OrderException(String message, Throwable cause) {
    super(message, cause);
}
```

There is no requirement to copy every constructor from `RuntimeException`.

### REMEMBER

```text
custom type
→ semantic contract

context field
→ structured data

cause
→ root diagnostics

hierarchy
→ caller-oriented handling categories
```

The final chapter combines the module into one decision model: where should code catch, wrap, log, retry, recover, or propagate?