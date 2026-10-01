# Side Effects, Exceptions, Trade-offs, and Practical Style

## <a id="side-effects-in-lambdas">Side Effects inside Lambdas</a>

A lambda is only syntax for implementing a functional-interface contract. Java does not require that implementation to be pure. A lambda can mutate an object, write a file, update a database, log, or call a remote service.

```java
Consumer<Order> publish = order -> eventBus.publish(order);
```

That can be exactly what the program needs. The problem starts when a lambda *looks* like a simple transformation or condition but performs hidden work whose timing or repetition matters.

A useful rule is to make effects visible through naming and placement. If behavior changes external state, readers should be able to discover that without inspecting every lambda body.

## <a id="hidden-mutation-state">Hidden Mutation and Captured State</a>

The effectively-final rule applies to a captured local variable's binding. It does not make the referenced object immutable.

```java
List<String> seen = new ArrayList<>();
Consumer<String> remember = value -> seen.add(value);
```

`seen` is not reassigned, so it can be captured. The `ArrayList` itself is still mutated every time `remember` runs.

This distinction matters because captured mutable state creates dependencies that are easy to miss. Results may depend on previous invocations, execution order, or concurrent access. If behavior must keep state, make that state ownership deliberate instead of assuming a lambda makes it harmless.

## <a id="checked-exception-constraint">Checked Exceptions and Target Function Types</a>

A lambda or method reference must obey the `throws` contract of its target function type. Standard interfaces such as `Function<T, R>` do not declare arbitrary checked exceptions.

```java
Function<Path, String> reader = path -> Files.readString(path); // does not compile
```

`Files.readString` can throw `IOException`, but `Function.apply` does not declare `IOException`. The lambda therefore cannot simply propagate that checked exception through this target type.

This is a type-contract issue, not a special weakness of lambda syntax. A named class implementing the same `Function` contract faces the same restriction.

## <a id="checked-exception-adaptation">Adapting Checked Exceptions Explicitly</a>

When functional code meets a checked exception, choose an adaptation that matches the API boundary instead of hiding the failure mechanically.

One option is to translate the checked exception into an appropriate unchecked exception:

```java
Function<Path, String> reader = path -> {
    try {
        return Files.readString(path);
    } catch (IOException ex) {
        throw new UncheckedIOException(ex);
    }
};
```

Another option is a domain-specific functional interface whose contract declares the checked exception:

```java
@FunctionalInterface
interface IOFunction<T, R> {
    R apply(T value) throws IOException;
}
```

You can also keep the operation in an ordinary method where checked-exception handling is clearer. Avoid generic "sneaky throw" helpers that bypass the visible contract; they make callers and tooling reason about a different failure model from the one the type declares.

## <a id="debugging-composed-behavior">Debugging Composed Behavior</a>

Composition reduces repetition, but long chains can make it harder to see which step produced a bad value or threw an exception. Debuggers and stack traces also have less descriptive context when every operation is an anonymous lambda.

Name important behavior when it helps investigation:

```java
Function<Request, Request> validate = this::validateRequest;
Function<Request, Command> toCommand = this::mapToCommand;

Function<Request, Command> prepare = validate.andThen(toCommand);
```

Named functions provide useful breakpoints and vocabulary. During diagnosis, splitting a large chain into intermediate variables is often clearer than adding logging inside every lambda.

Conciseness is not a debugging feature. Keep enough structure that a future reader can isolate each meaningful step.

## <a id="readability-vs-chaining">Readability vs Excessive Chaining</a>

Functional APIs make chaining easy, but a chain is readable only while the reader can still understand the data shape, failure behavior, and purpose of each step.

Watch for warning signs:

- several unrelated responsibilities inside one chain;
- nested lambdas whose parameters are difficult to track;
- repeated conversion between wrappers only to keep chaining;
- hidden side effects in operations that look like transformations;
- steps whose names do not explain the business meaning.

Extract a named method, introduce an intermediate variable, or use an ordinary control-flow statement when it makes the logic easier to verify. Fewer lines are not automatically simpler code.

## <a id="functional-vs-imperative-choice">Functional or Imperative Style?</a>

Functional style is strongest when a problem can be expressed as small behavior values, transformations, conditions, or policies that compose cleanly.

Imperative style can be clearer when the main story is a sequence of state changes: retry counters, resource lifecycles, state machines, complex early exits, or workflows where each step depends on mutations from the previous step.

```java
for (Task task : tasks) {
    if (!task.ready()) {
        continue;
    }
    execute(task);
    completed++;
}
```

There is no requirement to rewrite such code into a chain of lambdas. Choose the form that exposes the real control flow and invariants most directly.

## <a id="functional-vs-oop-choice">Functional Composition or Object-Oriented Structure?</a>

Passing a functional interface is useful when the variation is primarily **one behavior**: a comparison rule, transformation, validation rule, callback, or strategy that has little independent state.

An object-oriented abstraction is often clearer when the concept owns identity, multiple related operations, lifecycle, or substantial state.

```java
Comparator<Order> ordering = Comparator.comparing(Order::createdAt);
```

That single behavior fits naturally as a function-like contract. By contrast, a payment provider with authorization, capture, refund, credentials, and lifecycle rules is usually better represented as a richer object contract.

Java lets the styles cooperate: functional interfaces are interfaces, evaluating a lambda yields a value referring to an instance of its target functional-interface type, and object-oriented code can accept behavior parameters where that improves flexibility. Java does not guarantee a fresh lambda object for every evaluation, so code should not infer a stable implementation class or object identity from that fact.

## <a id="purity-immutability-boundary">Purity and Immutability: Paradigm Boundary</a>

Pure functions, referential transparency, immutability as a general design principle, and their language-neutral trade-offs belong to the Functional Programming paradigm module.

For Java Core, the boundary is simpler: **lambda syntax does not guarantee purity or immutability**. A Java lambda may read mutable fields, mutate captured objects, perform I/O, or call impure methods. Conversely, ordinary named Java methods can be pure even when no lambda is involved.

Keep that distinction when reading code. The mechanisms in this module make behavior first-class enough to pass and combine; they do not automatically give that behavior the semantic properties studied by the paradigm module.

## <a id="common-functional-pitfalls">Common Functional-Java Pitfalls</a>

Several recurring mistakes come from confusing compact syntax with better design:

- assuming every lambda is pure or thread-safe;
- mutating captured objects and forgetting that later calls share the same state;
- choosing `compose`/`andThen` in the wrong semantic order even when the types compile;
- forcing a method reference when a lambda would make argument mapping clearer;
- hiding checked-exception translation inside generic wrappers;
- calling `Optional.get()` or spreading `Optional` everywhere without a clear absence contract;
- building chains so long that debugging and business intent disappear;
- assuming knowledge of lambdas is enough to infer Stream execution semantics.

The common fix is not "avoid functional style". It is to keep type contracts, state, effects, execution order, and module boundaries explicit.

## <a id="functional-programming-synthesis">Functional Programming in Java: End-to-End Mental Model</a>

The Java-specific mental model can now be read as one connected flow:

```text
varying behavior
    -> functional-interface contract
    -> lambda or method/constructor reference
    -> target typing checks parameter/result compatibility
    -> optional capture of surrounding values
    -> pass, return, or compose the behavior
    -> invoke it through the functional-interface method
    -> keep state, effects, exceptions, and readability explicit
```

Standard interfaces in `java.util.function` provide reusable contracts; custom functional interfaces are appropriate when domain meaning or exception rules require a different contract. `Optional` applies the same style to explicit absence at selected API boundaries. Stream APIs reuse these mechanisms by accepting functional-interface values, while their own processing semantics live in the Stream API module.

Use functional Java where behavior parameterization and composition reduce duplication and expose intent. Use ordinary imperative or object-oriented structure where stateful workflow, richer object responsibilities, or debugging clarity make them a better fit. The goal is not the maximum number of lambdas; it is code whose behavior and constraints remain easy to understand.
