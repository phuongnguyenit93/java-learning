# Method and Constructor References

## <a id="method-reference-model">What Is a Method Reference?</a>

A method reference is a compact way to describe behavior that already exists as a method or constructor. It plays the same role as a lambda in a target-typed context: Java still needs a functional-interface type to know the parameter and result contract.

Think of it as saying **"use this existing operation as the implementation of that functional contract"**. Writing the reference does not call the method immediately.

```java
Function<String, Integer> parse = Integer::parseInt;

int result = parse.apply("42");
```

The equivalent lambda is `text -> Integer.parseInt(text)`. The method reference is useful only when removing that wrapper makes the intent clearer.

## <a id="static-method-reference">Static Method References</a>

A static method reference has the form `TypeName::staticMethod`. The parameters of the target functional interface are passed to the referenced static method in the same order.

```java
Function<String, Integer> parse = Integer::parseInt;
BinaryOperator<Integer> max = Math::max;
```

For `parse`, `Function.apply(String)` supplies the argument to `Integer.parseInt(String)`. For `max`, the two `BinaryOperator` arguments become the two arguments of `Math.max`.

The target type still matters when a static method is overloaded. The text before and after `::` is not enough by itself to choose one overload.

## <a id="bound-instance-method-reference">Bound Instance Method References</a>

A bound instance reference has the form `object::instanceMethod`. The receiver object is already fixed, so the target functional interface supplies only the method's remaining arguments.

```java
String prefix = "ID-";
Function<String, String> addPrefix = prefix::concat;

String value = addPrefix.apply("17"); // "ID-17"
```

Here `prefix` is the receiver of `concat`. `Function.apply` contributes the single `String` argument.

Because the receiver is captured when the reference is created, be careful when that receiver is mutable. Reusing the same method reference can observe later changes to the same object.

## <a id="unbound-instance-method-reference">Unbound Instance Method References</a>

An unbound instance reference has the form `TypeName::instanceMethod`. No receiver object is fixed yet. The target functional interface therefore supplies the receiver as its first argument, followed by the method arguments.

```java
Function<String, String> trim = String::trim;
BiPredicate<String, String> startsWith = String::startsWith;

trim.apply("  Java  ");            // "Java"
startsWith.test("Java", "Ja");   // true
```

For `String::trim`, the `Function` input becomes the `String` receiver. For `String::startsWith`, the first `BiPredicate` argument is the receiver and the second is the prefix argument.

This receiver mapping is the main difference between `someString::method` and `String::method`.

## <a id="constructor-reference">Constructor References</a>

A constructor reference has the form `TypeName::new`. It lets a functional interface describe object creation without writing a lambda whose only job is `new`.

```java
Supplier<List<String>> listFactory = ArrayList::new;
Function<String, StringBuilder> builderFactory = StringBuilder::new;

List<String> names = listFactory.get();
StringBuilder builder = builderFactory.apply("Java");
```

The target type determines which constructor shape is required. A zero-argument `Supplier` looks for a compatible zero-argument constructor, while a one-argument `Function` looks for a compatible one-argument constructor.

Generic type inference still applies. The reference does not bypass normal constructor accessibility or overload-resolution rules.

Arrays also support constructor references. In that form, the target functional interface supplies the requested array length:

```java
IntFunction<String[]> stringArray = String[]::new;
String[] values = stringArray.apply(3);
```

For a bound reference such as `receiver::method`, the receiver expression is evaluated when the method-reference expression itself is evaluated. If that expression produces `null`, creating the bound method reference throws `NullPointerException`; the null check is not postponed until the functional method is invoked.

## <a id="method-reference-target-adaptation">Adapting to the Target Function Type</a>

A method reference has no useful standalone function type. Java interprets it against a target functional interface and checks whether the referenced invocation can satisfy that interface's function type.

The important pieces are:

- the number and order of target parameters;
- receiver mapping for instance methods;
- parameter conversions allowed by Java method invocation;
- compatibility of the referenced result with the target result;
- overload resolution when several members share the same name.

```java
Function<String, Integer> decimal = Integer::parseInt;
ToIntFunction<String> decimalPrimitive = Integer::parseInt;
```

Both references point to the same operation, but the target contracts differ: one produces `Integer`, while the other produces primitive `int`. Normal boxing/unboxing and target typing determine whether the assignment is valid.

## <a id="method-reference-result-adaptation">Result Compatibility and Discarded Return Values</a>

When the target function type returns a value, the referenced method must produce a compatible result. When the target returns `void`, Java may invoke a compatible method and discard its result.

```java
Function<String, String> trimAndKeep = String::trim;
Consumer<String> trimAndDiscard = String::trim;
```

Both can invoke `String.trim()`. The `Function` keeps the returned `String`; the `Consumer` ignores it.

Discarding a result can be legal yet misleading. A `Consumer` that calls a value-returning method may make readers wonder whether the result was accidentally lost, so use this adaptation only when the side effect or invocation itself is clearly the purpose.

## <a id="overloaded-method-reference">Overloaded Method References</a>

If the referenced name is overloaded, Java uses the target function type to select a compatible member. This often works naturally, but ambiguous target contexts can require a more explicit lambda or cast.

```java
Function<String, Integer> parseInt = Integer::valueOf;
Function<String, Long> parseLong = Long::valueOf;
```

The assignment provides enough information for each reference. Trouble appears when several overloads are compatible with the same surrounding call or when the target type itself is not yet known.

In those cases, a lambda can make parameter types or the exact call explicit and is often easier to debug than forcing a complicated cast around a method reference.

## <a id="method-reference-vs-lambda">Method Reference or Lambda?</a>

Use a method reference when the lambda would only forward its arguments to one existing method or constructor and the referenced name explains the behavior well.

```java
names.forEach(System.out::println);       // concise and recognizable

Function<String, String> normalized =
        text -> text == null ? "" : text.trim(); // lambda carries real logic
```

A lambda is usually clearer when you need validation, argument rearrangement, constants, multiple calls, control flow, or a name that would otherwise hide important intent.

Do not treat method references as a goal in themselves. They are syntax for reusing existing behavior under a functional-interface contract; readability is the deciding trade-off.
