# Custom Exceptions

Not every failure needs a new exception class. A custom exception is valuable when the new type **adds meaning**, creates a useful abstraction boundary, or gives callers a meaningful handling category.

## <a id="custom-exception-purpose">When Custom Exceptions Help</a>

Custom exceptions are useful when they:

- express a domain/application failure clearly;
- translate low-level failures into higher-level vocabulary;
- let callers catch a meaningful category;
- carry structured context that a generic message cannot express well.

Avoid creating a new exception merely to rename an existing one without adding contract or context.

## <a id="exception-context">Preserving Context and Cause</a>

```java
throw new OrderLoadException(orderId, ex);
```

A useful custom exception may retain an identifier, operation context, and the original cause.

Avoid placing secrets or sensitive values in exception messages/context because they often reach logs.

## <a id="exception-hierarchy-design">Designing Exception Hierarchies</a>

A small hierarchy with meaningful handling categories is usually better than many classes that differ only by name.

```text
OrderException
├── OrderNotFoundException
└── OrderValidationException
```

The hierarchy should reflect how callers want to handle failures, not every internal implementation detail.

The final chapter puts all of the mechanics into application-level exception design.
