# Standard Functional Interfaces

## <a id="java-util-function-overview">The java.util.function Families</a>

Java provides reusable functional-interface families in `java.util.function` so common behavior shapes do not need a new custom interface every time.

The main question is not "which interface name do I remember?" but **what contract does the behavior have?** Ask:

- Does it transform one value into another? → `Function`.
- Does it answer a yes/no question? → `Predicate`.
- Does it receive a value and perform an action? → `Consumer`.
- Does it produce a value without receiving one? → `Supplier`.
- Do input and output have the same type? → an operator interface may fit.
- Does the behavior need two inputs? → one of the `Bi...` families may fit.

```java
Function<String, Integer> length = String::length;
Predicate<String> nonEmpty = text -> !text.isEmpty();
Consumer<String> printer = System.out::println;
Supplier<UUID> idFactory = UUID::randomUUID;
```

These interfaces are ordinary Java interfaces with one functional contract. Lambdas and method references are concise ways to supply implementations for them.

## <a id="function-contract">Function&lt;T, R&gt;</a>

`Function<T, R>` represents a transformation from one input value of type `T` to one result of type `R`.

Its functional method is:

```java
R apply(T value);
```

Example:

```java
Function<String, Integer> length = text -> text.length();

int result = length.apply("Java"); // 4
```

Use `Function` when the essential contract is **input → output**. The output may have the same type as the input, but when that relationship is guaranteed, `UnaryOperator<T>` communicates the intent more precisely.

`Function` is also used heavily in composition because its output can become another function's input. The composition rules themselves are covered in the later behavior-composition chapter.

## <a id="predicate-contract">Predicate&lt;T&gt;</a>

`Predicate<T>` represents a condition about a value of type `T`. Its result is always `boolean`.

```java
Predicate<String> nonEmpty = text -> !text.isEmpty();
Predicate<Integer> adult = age -> age >= 18;

boolean accepted = adult.test(20); // true
```

Its functional method is `boolean test(T value)`.

Use `Predicate` when the behavior answers a question such as "is this value valid?", "does it match?", or "should it be accepted?". Giving a predicate a meaningful variable or parameter name often makes the calling code read like a business rule.

Predicates can be combined with operations such as `and`, `or`, and `negate`; composition is discussed later so this chapter can focus first on choosing the correct contract.

## <a id="consumer-contract">Consumer&lt;T&gt;</a>

`Consumer<T>` receives a value and returns no result. Its functional method is:

```java
void accept(T value);
```

Example:

```java
Consumer<String> printer = text -> System.out.println(text);
printer.accept("hello");
```

A consumer usually represents an action with an observable effect: logging, sending data, updating an object, invoking another service, and so on.

Because a `Consumer` returns `void`, it is a poor fit when callers need a transformed value. In that case, a `Function` or another value-producing contract is usually clearer.

Side effects are legal in Java functional code, but their placement affects readability and testability. The final chapter returns to that design trade-off.

## <a id="supplier-contract">Supplier&lt;T&gt;</a>

`Supplier<T>` produces a value without receiving an argument through its functional method:

```java
T get();
```

Example:

```java
Supplier<UUID> idFactory = UUID::randomUUID;
UUID id = idFactory.get();
```

The important idea is **deferred production**. Passing a `Supplier<T>` passes the recipe for obtaining a value, rather than the value that has already been computed.

```java
Supplier<String> expensiveMessage = () -> buildExpensiveMessage();
```

Whether and when `get()` is called is controlled by the receiving code. This is useful for factories, lazy fallback values, test data providers, and other cases where value creation should happen on demand.

## <a id="operator-contracts">UnaryOperator&lt;T&gt; and BinaryOperator&lt;T&gt;</a>

The operator interfaces preserve a same-type relationship: `UnaryOperator<T>` specializes `Function<T, T>`, while `BinaryOperator<T>` specializes `BiFunction<T, T, T>`.

`UnaryOperator<T>` represents one input and one result of type `T`:

```java
UnaryOperator<String> normalize = text -> text.trim().toLowerCase();
```

`BinaryOperator<T>` represents two `T` inputs and one `T` result:

```java
BinaryOperator<Integer> max = (left, right) -> Math.max(left, right);
```

Use these interfaces when the same-type relationship is meaningful. `UnaryOperator<String>` communicates "String in, String out" more directly than `Function<String, String>`, while `BinaryOperator<Integer>` communicates that two values of one domain type are combined into another value of that same type.

## <a id="bi-functional-interfaces">BiFunction, BiConsumer, and BiPredicate</a>

The `Bi...` interfaces model common contracts with two input values.

```java
BiFunction<BigDecimal, Integer, BigDecimal> multiply =
        (price, quantity) -> price.multiply(BigDecimal.valueOf(quantity));

BiPredicate<String, Integer> minimumLength =
        (text, minimum) -> text.length() >= minimum;

BiConsumer<Map<String, Integer>, String> increment =
        (counts, key) -> counts.merge(key, 1, Integer::sum);
```

Their shapes are:

- `BiFunction<T, U, R>`: two inputs, one result;
- `BiPredicate<T, U>`: two inputs, `boolean` result;
- `BiConsumer<T, U>`: two inputs, no result.

There is no standard `TriFunction` family in `java.util.function`. If a behavior naturally requires more parameters, consider whether a small domain object would make the contract clearer before creating a custom multi-argument interface.

## <a id="primitive-specializations">Primitive Specializations</a>

Generic functional interfaces use reference types, so primitive values used with types such as `Function<Integer, Integer>` may require boxing and unboxing.

`java.util.function` therefore includes primitive-specialized variants for common `int`, `long`, and `double` shapes, for example:

```java
IntPredicate positive = value -> value > 0;
IntUnaryOperator square = value -> value * value;
ToIntFunction<String> length = String::length;
IntFunction<String> label = value -> "#" + value;
```

These interfaces do not introduce a different programming model. They express the same behavior shapes while avoiding unnecessary wrapper objects in primitive-heavy code.

Do not select a primitive specialization automatically for every primitive occurrence. Prefer it when the API already expects that specialization or when avoiding boxing is relevant to the code path; otherwise, clarity remains the first concern.

## <a id="generic-input-output-flow">Generic Input and Output Type Flow</a>

The type parameters in standard functional interfaces make the direction of data explicit.

For `Function<T, R>`:

```text
T  ──apply──>  R
```

For `BiFunction<T, U, R>`:

```text
T + U  ──apply──>  R
```

For `Predicate<T>`, `T` is the input and `boolean` is fixed as the result. For `Consumer<T>`, `T` is the input and there is no result. For `Supplier<T>`, there is no input parameter and `T` is the produced result.

This relationship matters when reading generic APIs:

```java
Function<Order, BigDecimal> total = Order::total;
```

The type tells the reader the complete high-level flow before inspecting the lambda body: an `Order` goes in and a `BigDecimal` comes out.

Later composition becomes easier to reason about when the output type of one behavior matches the input type expected by the next.

## <a id="functional-interfaces-outside-java-util-function">Functional Interfaces outside java.util.function</a>

`java.util.function` is the main library of general-purpose function shapes, but it does not own every functional interface in the JDK.

Examples include:

```java
Runnable task = () -> doWork();
Callable<String> loader = () -> loadValue();
Comparator<String> byLength =
        (left, right) -> Integer.compare(left.length(), right.length());
```

`Runnable`, `Callable<V>`, and `Comparator<T>` live in other packages because they belong to other API domains, yet each exposes a compatible single functional contract and can therefore be targeted by a lambda or method reference.

The lesson is to recognize the **functional-interface contract**, not to assume that every lambda target must come from `java.util.function`.

## <a id="standard-vs-custom-functional-interface">Standard vs Domain-Specific Functional Interfaces</a>

Prefer a standard functional interface when its contract communicates the intent well:

```java
Predicate<Order> eligible;
Function<Order, BigDecimal> totalCalculator;
Supplier<Clock> clockSupplier;
```

Create a domain-specific interface when the domain meaning is important enough to deserve its own contract:

```java
@FunctionalInterface
interface FraudRule {
    boolean isSuspicious(Payment payment);
}
```

The choice is a readability and API-design decision, not a contest to minimize the number of custom types.

Use a standard interface when:

- its input/output shape already explains the behavior;
- callers benefit from familiar Java vocabulary;
- standard composition methods are useful.

Use a custom interface when:

- the domain role needs an explicit name;
- the contract needs a checked exception or another domain-specific promise;
- using `Function`/`Predicate` would hide important meaning from the API.

Whichever form you choose, keep the functional contract focused on one coherent behavior.
