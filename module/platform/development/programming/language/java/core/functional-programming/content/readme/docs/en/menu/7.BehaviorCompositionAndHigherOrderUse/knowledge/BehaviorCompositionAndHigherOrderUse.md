# Behavior Composition and Higher-Order Use

## <a id="higher-order-methods">Methods That Receive or Return Behavior</a>

Java does not have a separate built-in function type. Instead, a method can receive or return behavior through a functional-interface type such as `Function`, `Predicate`, `Consumer`, or a domain-specific functional interface.

That gives Java a practical form of higher-order programming: behavior can be passed around as a value while the functional interface keeps the parameter and result contract explicit.

```java
static <T, R> R apply(T value, Function<T, R> operation) {
    return operation.apply(value);
}

String result = apply(" java ", String::trim);
```

The method owns *when* the behavior runs. The caller chooses *which* behavior to provide.

## <a id="passing-behavior">Passing Behavior to a Method</a>

Passing behavior is useful when the algorithm is stable but one decision varies. Instead of hard-coding every variation with `if` branches or subclasses, the method accepts the varying rule as a parameter.

```java
static List<String> select(
        List<String> values,
        Predicate<String> rule) {
    List<String> result = new ArrayList<>();
    for (String value : values) {
        if (rule.test(value)) {
            result.add(value);
        }
    }
    return result;
}

List<String> longNames = select(names, name -> name.length() >= 5);
```

The important design step is choosing a functional contract that expresses the role clearly. A `Predicate<T>` communicates "decide true/false" better than a generic custom interface with an unclear method name.

## <a id="returning-behavior">Returning Behavior from a Method</a>

A method can also build behavior and return it for later use. This is useful for factories of validation rules, transformations, callbacks, or policies.

```java
static Predicate<String> hasMinLength(int min) {
    return text -> text != null && text.length() >= min;
}

Predicate<String> atLeastFive = hasMinLength(5);
boolean valid = atLeastFive.test("Java 21");
```

At runtime, the returned value is an instance of the target functional-interface type. The caller should depend on that interface contract rather than on the implementation class or object identity, and does not need to know whether the behavior came from a lambda, method reference, or named class.

## <a id="returned-behavior-capture">Returning Behavior with Captured Values</a>

Returned behavior becomes especially useful when it remembers values from the method call that created it. The lambda captures those values and can use them later.

```java
static Function<String, String> prefixWith(String prefix) {
    return value -> prefix + value;
}

Function<String, String> errorLabel = prefixWith("ERROR: ");
System.out.println(errorLabel.apply("Disk full"));
```

Each invocation of `prefixWith` creates behavior associated with that invocation's `prefix`. The captured local variable must obey the `final`/effectively-final rule discussed earlier.

Capturing a reference does not freeze the referenced object. If the returned behavior observes mutable state, later mutations can change its result and make the behavior harder to reason about.

## <a id="function-composition">Function Composition with compose and andThen</a>

`Function` can combine two transformations into one reusable transformation. The two main methods differ only in execution direction.

```java
Function<String, String> trim = String::trim;
Function<String, Integer> length = String::length;

Function<String, Integer> trimThenLength = trim.andThen(length);
Function<String, Integer> sameFlow = length.compose(trim);

trimThenLength.apply(" Java "); // 4
```

For `f.andThen(g)`, data flows `f -> g`. For `g.compose(f)`, data also flows `f -> g`. Reading the types helps: the output of the first function must be compatible with the input of the next one.

Composition is valuable when the named pieces are meaningful on their own. Chaining many tiny functions can become harder to follow than one straightforward method.

## <a id="predicate-composition">Predicate Composition</a>

`Predicate<T>` provides `and`, `or`, and `negate` so small conditions can form larger rules.

```java
Predicate<String> notBlank = text -> text != null && !text.isBlank();
Predicate<String> shortEnough = text -> text.length() <= 20;

Predicate<String> validName = notBlank.and(shortEnough);
```

`and` and `or` short-circuit: the second predicate is evaluated only when the first result does not already determine the answer. That matters if a later predicate assumes an earlier guard has passed.

Predicates should ideally behave like questions. Hidden mutation or I/O inside a predicate makes a condition unexpectedly expensive or stateful.

## <a id="consumer-composition">Consumer.andThen</a>

`Consumer<T>` represents behavior whose contract does not return a result. `andThen` combines consumers in sequence.

```java
Consumer<String> log = value -> System.out.println("LOG: " + value);
Consumer<String> audit = value -> auditStore.add(value);

Consumer<String> logAndAudit = log.andThen(audit);
```

The first consumer runs before the second. If the first throws an exception, the second is not executed.

Because consumers commonly contain side effects, their order is observable. Composition should make that order clearer, not hide a fragile sequence of external actions.

## <a id="composition-order">Composition Order and Data Flow</a>

Most composition bugs come from reading names instead of tracing data. A reliable mental model is to write the value flow explicitly.

```text
input
  -> trim
  -> parse
  -> validate
  -> result
```

Then choose APIs that preserve that flow. With `Function`, `andThen` reads left-to-right, while `compose` names the later function first.

Types provide an additional safety check. If one function returns `Integer`, the next function must accept `Integer` or a compatible type. Compilation can catch many ordering mistakes, but it cannot tell whether a semantically valid order is the one your business rule intended.

## <a id="composition-side-effects">Side Effects inside Composed Behavior</a>

Java allows composed lambdas to mutate objects, write logs, call databases, or perform any other side effect. Functional interfaces do not enforce purity.

The risk is that composition can visually resemble a data transformation while secretly depending on execution count and order.

```java
Function<Order, Order> record = order -> {
    auditService.record(order);
    return order;
};
```

If such a function is composed or reused, the audit call runs every time the function is invoked. That may be correct, but the effect should be obvious from naming and placement.

Prefer pure transformations for reusable composition when practical. When effects are required, keep them explicit and close to the boundary where they belong.
