# Functional Programming and the Stream API Boundary

## <a id="stream-functional-interface-bridge">Why Stream APIs Consume Functional Interfaces</a>

The Stream API needs callers to supply behavior such as "keep this element", "transform this value", or "do this with each value". Functional interfaces give those behaviors concrete Java types.

For example, callers can supply contracts you already know from `java.util.function`:

```java
Predicate<String> keep = name -> !name.isBlank();
Function<String, String> normalize = String::trim;
Consumer<String> print = System.out::println;

names.stream().filter(keep);
names.stream().map(normalize);
names.stream().forEach(print);
```

The important connection in this module is the type relationship: lambdas and method references become values of those functional-interface types, and Stream methods receive them as arguments.

How a Stream schedules, delays, combines, or executes its operations is separate Stream API knowledge.

## <a id="stream-behavior-contracts">Functional Contracts Commonly Used by Streams</a>

Different Stream methods ask for different behavior contracts. You can often understand what a method expects by reading the functional-interface type in its signature.

- `Predicate<T>` answers a yes/no question about a value.
- `Function<T, R>` transforms one value into another form.
- `Consumer<T>` performs an action without returning a result through the functional contract.
- `Supplier<T>` can provide values when an API needs a value-producing behavior.

These interfaces are not Stream-specific. They are general Java contracts reused by the Stream API, collections utilities, asynchronous APIs, application code, and many libraries.

When learning this chapter, focus on recognizing the contract. Detailed Stream operation categories and execution rules belong in the Stream API module.

## <a id="stream-lambda-method-reference">Lambdas and Method References in Stream Code</a>

Stream code commonly makes functional interfaces visible through lambda and method-reference syntax.

```java
names.stream()
        .filter(name -> !name.isBlank())
        .map(String::trim);
```

Here the lambda can satisfy a `Predicate<String>`, while `String::trim` can satisfy the mapping function expected by `map`.

This example is only evidence of the connection: **Stream method -> functional-interface parameter -> lambda/method reference**. The semantics of the Stream chain itself are owned by the Stream API module.

## <a id="collection-vs-stream-boundary">Collection Data vs Stream Processing</a>

A `Collection` is primarily a data structure that owns or provides access to elements. A `Stream` is an API abstraction used to describe processing over a source of elements; it is not simply another collection type.

From the functional-programming perspective, the useful observation is that Stream processing is parameterized by behavior. The source may come from a collection, but the `Predicate`, `Function`, or `Consumer` passed to Stream methods remains the same kind of functional-interface value learned in this module.

Questions such as when elements are processed, how operations are categorized, or how results are accumulated require Stream-specific rules and should be learned in the dedicated module.

## <a id="stream-mechanics-boundary">What Belongs to the Stream API Module</a>

This module owns the Java functional mechanisms that Stream APIs *consume*: functional interfaces, lambdas, method references, target typing, capture, and behavior composition.

The dedicated Stream API module owns Stream mechanics, including topics such as:

- creating Streams and understanding Stream sources;
- intermediate and terminal operation semantics;
- lazy evaluation and execution timing;
- reduction and collectors;
- encounter order and ordering behavior;
- parallel Stream behavior and its constraints.

Those subjects are intentionally not expanded here. Knowing the boundary prevents two common confusions: a lambda is not a Stream, and learning lambda syntax does not teach Stream execution semantics.

## <a id="stream-learning-handoff">When to Continue into the Stream API Module</a>

Move to the Stream API module once the remaining question is about **how Stream processing behaves**, rather than **how Java represents the behavior passed into it**.

Stay in this module when you are asking questions such as:

- Why does this parameter have type `Predicate<T>` or `Function<T, R>`?
- Why is this lambda compatible with one method call but not another?
- Can this method reference satisfy the target function type?
- What variable-capture rules apply inside the lambda?

Continue into Stream API when the question is about the Stream's own lifecycle, operation semantics, execution, accumulation, ordering, or parallel behavior.
