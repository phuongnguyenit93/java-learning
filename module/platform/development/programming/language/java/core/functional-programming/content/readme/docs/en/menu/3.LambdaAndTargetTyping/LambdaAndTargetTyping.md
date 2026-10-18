# Lambda Expressions and Target Typing

## <a id="lambda-expression-model">What Is a Lambda Expression?</a>

A lambda expression is Java syntax for supplying behavior to a compatible functional-interface target type.

```java
Predicate<String> nonEmpty = text -> !text.isEmpty();
```

The lambda `text -> !text.isEmpty()` does not declare a named method and does not introduce a new function type of its own. The surrounding `Predicate<String>` target tells the compiler that the lambda receives a `String` and must produce a `boolean`.

Evaluating a lambda expression yields a value referring to an instance of the target functional-interface type. Java does not guarantee that each evaluation allocates a fresh object, so code should depend on the interface contract rather than assumptions about the runtime implementation class or object identity.

The useful mental model is: **a lambda is an expression whose meaning is completed by its target functional interface**.

## <a id="lambda-syntax-forms">Lambda Syntax Forms</a>

Lambda syntax has a parameter list, the `->` token, and either an expression body or a block body.

```java
// No parameters
Supplier<Long> now = () -> System.currentTimeMillis();

// One parameter; parentheses may be omitted
Predicate<String> nonEmpty = text -> !text.isEmpty();

// Multiple parameters
Comparator<String> byLength =
        (left, right) -> Integer.compare(left.length(), right.length());

// Block body
Function<String, Integer> parse = text -> {
    String trimmed = text.trim();
    return Integer.parseInt(trimmed);
};
```

Expression bodies are concise when one expression communicates the behavior. Block bodies are useful when the behavior requires several statements, local variables, or explicit `return` statements.

Choose the shortest form that remains easy to read. Compressing several business steps into one dense expression usually hurts more than it helps.

## <a id="target-typing">Target Typing</a>

Lambda expressions are **target typed**. The compiler uses the surrounding expected functional-interface type to interpret the lambda.

```java
Predicate<String> rule = text -> text.length() > 3;
```

From `Predicate<String>`, the compiler knows that `text` is a `String` and that the body must be compatible with a `boolean` result.

The same lambda text can target different compatible interfaces:

```java
Callable<String> first = () -> "done";
Supplier<String> second = () -> "done";
```

The body is identical, but the resulting values have different Java types and therefore different API contracts.

Because target typing is essential, a lambda cannot normally stand alone without a context that supplies a target functional interface.

## <a id="lambda-parameter-typing">Explicit and Inferred Parameter Types</a>

Most lambda parameter types can be inferred from the target function type:

```java
BiFunction<Integer, Integer, Integer> add = (left, right) -> left + right;
```

They can also be written explicitly:

```java
BiFunction<Integer, Integer, Integer> add =
        (Integer left, Integer right) -> left + right;
```

Since Java 11, `var` may be used for lambda parameters when annotations or a uniform explicit style are useful:

```java
BiFunction<Integer, Integer, Integer> add =
        (var left, var right) -> left + right;
```

Within one lambda parameter list, use a consistent parameter-declaration style. Do not mix an implicitly typed parameter with an explicitly typed or `var` parameter.

Inference removes repetition; it does not make the parameters dynamically typed. Their compile-time types still come from the target functional interface.

## <a id="lambda-body-result-compatibility">Lambda Body and Result Compatibility</a>

The lambda body must match the result expected by the target function type.

For a value-producing contract, an expression body must produce a compatible value. A block body is structurally **value-compatible** when it cannot complete normally and every `return` statement in the block has the form `return expression;`. For a particular target result type, each such result expression must then be compatible with that target type:

```java
Function<String, Integer> length = text -> text.length();

Function<String, Integer> parsedLength = text -> {
    String trimmed = text.trim();
    return trimmed.length();
};

Supplier<String> alwaysFails = () -> {
    throw new IllegalStateException("failed");
};
```

The `alwaysFails` block is valid for a value-returning target even though it never reaches a `return`, because the block cannot complete normally.

For a `void`-producing contract, a block body does not return a value. An expression body may also be a Java **statement expression** whose produced value is simply discarded:

```java
Consumer<String> printer = text -> System.out.println(text);
Consumer<String> trimAndDiscard = text -> text.trim();

Consumer<String> normalizedPrinter = text -> {
    String normalized = text.trim();
    System.out.println(normalized);
};
```

Here `text.trim()` returns a `String`, but that result is discarded because the target function type returns `void`. This does not make every value expression `void`-compatible; Java applies its specific statement-expression compatibility rules.

Block lambdas must satisfy Java's normal reachability and return rules for the target result. A block targeting a value-returning function cannot complete normally by falling through to the closing brace.

Checked exceptions are also constrained by the target function type; that practical limitation is developed in the final chapter of this module.

## <a id="lambda-target-contexts">Where Lambda Target Types Come From</a>

Common target-typing contexts include assignment, method invocation, return statements, and explicit casts.

```java
// Assignment context
Predicate<String> valid = text -> !text.isBlank();

// Method-invocation context
runWhenReady(() -> startService());

// Return context
Predicate<String> buildRule() {
    return text -> text.length() >= 3;
}

// Cast context
Object rule = (Predicate<String>) text -> !text.isEmpty();
```

In each case, the surrounding Java construct supplies the expected functional-interface type. That expected type determines parameter types and the result contract before the lambda can be type-checked.

If the context does not identify one compatible target clearly, compilation may fail rather than guessing which behavior type the programmer intended.

## <a id="lambda-execution-timing">Declaring Behavior vs Executing It</a>

Creating a lambda value does not automatically run its body.

```java
Supplier<String> message = () -> {
    System.out.println("building message");
    return "ready";
};
```

At this point the behavior has been created, but `building message` has not been printed. The body runs when the functional method is invoked:

```java
String value = message.get();
```

This distinction matters for deferred work. A lambda can capture configuration now and be executed later by another method or component.

Do not assume that "passing a lambda" means the lambda runs immediately. Execution timing is controlled by the API that receives and invokes the functional-interface value.

## <a id="lambda-overload-ambiguity">Overload Ambiguity with Lambdas</a>

Overloaded methods can create ambiguity when the same lambda shape is compatible with more than one functional-interface parameter.

```java
void use(Callable<String> task) { }
void use(Supplier<String> task) { }

// use(() -> "done"); // ambiguous
```

Both target types accept a no-argument behavior returning `String`, so the lambda text alone does not select one overload.

An explicit target can disambiguate the call:

```java
use((Supplier<String>) () -> "done");
```

or the behavior can first be assigned to a named variable with the intended type.

Avoid overload sets that differ only by unrelated functional-interface types with the same lambda shape when you control the API design. They make otherwise simple lambda calls harder for both the compiler and the reader to interpret.
