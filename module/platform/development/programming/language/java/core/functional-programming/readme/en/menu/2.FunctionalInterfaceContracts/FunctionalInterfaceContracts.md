# Functional Interfaces and SAM Contracts

## <a id="functional-interface-purpose">What Makes an Interface Functional?</a>

A functional interface is an interface whose abstract behavior can be represented by one functional contract. That property lets Java use the interface as a target type for a lambda expression or method reference.

```java
@FunctionalInterface
interface TextRule {
    boolean test(String text);
}

TextRule nonEmpty = text -> !text.isEmpty();
```

The important point is the **contract**, not the number of methods written in the source file. A functional interface may also contain `default`, `static`, and `private` methods because those methods do not add another abstract behavior that a lambda must implement.

Java already had interfaces such as `Runnable` and `Comparator<T>` before or alongside lambda-focused APIs. Functional interfaces connect that ordinary interface model to behavior values without creating a separate function-type hierarchy outside Java's type system.

## <a id="sam-contract">The Single Abstract Method Contract</a>

SAM stands for **Single Abstract Method**. The beginner mental model is simple: a lambda needs one abstract operation to implement.

```java
@FunctionalInterface
interface Transformer<T, R> {
    R apply(T value);
}
```

`Transformer<T, R>` has one abstract operation, `apply`, so a compatible lambda can provide that behavior:

```java
Transformer<String, Integer> length = text -> text.length();
```

However, Java does not determine functional-interface status by naively counting every `abstract` declaration. Inheritance and methods matching public instance methods of `Object` affect the calculation. The next sections cover those rules.

For day-to-day code, remember the intent: after Java applies the functional-interface rules, there must be one coherent abstract contract for the lambda or method reference to implement.

## <a id="functional-interface-function-type">The Function Type of a Functional Interface</a>

The functional interface provides more than the fact that "one method exists." Java derives a **function type** from that abstract contract. Conceptually, the function type describes:

- the parameter types accepted by the behavior;
- the result type it must produce, or `void` if there is no result;
- the checked exceptions permitted by the contract.

For example:

```java
@FunctionalInterface
interface Parser<T> {
    T parse(String text) throws IOException;
}
```

The target `Parser<Integer>` describes behavior that accepts a `String`, produces an `Integer`, and may throw `IOException`.

This function type is what later chapters rely on when checking whether a lambda body, method reference, return value, or checked exception is compatible with its target. It is a compiler concept tied to the target interface; Java does not expose a universal standalone `FunctionType` class for it.

## <a id="sam-object-method-rule">Object Method Signatures in SAM Rules</a>

An interface may explicitly redeclare a method whose signature matches a **public instance method of `Object`**, such as `equals(Object)`. Such a declaration does not create an additional functional-interface abstract operation.

```java
@FunctionalInterface
interface Matcher {
    boolean matches(String value);

    @Override
    boolean equals(Object other);
}
```

`Matcher` can still be functional because `equals(Object)` corresponds to a public `Object` method, leaving `matches` as the functional contract.

This rule should not be generalized to every method name found in `Object`. The exclusion is based on the relevant public instance-method signature rules, not simply on spelling a familiar name such as `clone` or `finalize`.

## <a id="sam-inherited-method-rule">Inherited Override-Equivalent Abstract Methods</a>

Inheritance can produce several abstract declarations that still represent one logical operation. If the inherited methods are compatible under Java's override rules, the interface may still have a single functional contract.

```java
interface Left {
    CharSequence value();
}

interface Right {
    String value();
}

@FunctionalInterface
interface Combined extends Left, Right {
}
```

`String` is a covariant return type for `CharSequence`, so the inherited `value()` declarations can describe one compatible contract. A lambda targeting `Combined` therefore provides one `value` behavior.

By contrast, unrelated abstract methods such as `read()` and `write()` do not collapse into one contract. Neither do inherited declarations whose signatures or return types cannot form a valid overriding relationship.

This is why "count the abstract methods in the source" is only a shortcut, not the actual rule.

## <a id="functional-interface-annotation">@FunctionalInterface</a>

`@FunctionalInterface` asks the compiler to verify that the annotated interface really satisfies the functional-interface rules.

```java
@FunctionalInterface
interface Validator<T> {
    boolean isValid(T value);
}
```

The annotation is useful documentation and a safety check. If a later edit adds an incompatible second abstract contract, compilation fails near the interface definition instead of letting the design silently stop being lambda-compatible.

The annotation is **not required**. An interface that structurally satisfies the rules is still a functional interface even without `@FunctionalInterface`.

Use the annotation for interfaces intentionally designed as functional contracts. Its presence communicates that keeping one functional contract is part of the API design.

## <a id="non-abstract-interface-methods">Default, Static, and Private Interface Methods</a>

`default`, `static`, and `private` interface methods already have implementations, so they do not add another abstract behavior that a lambda must supply.

`default` and `static` interface methods are available from Java 8. Private interface methods were added in Java 9, so the `private static` helper in the example below is not valid when compiling with Java 8 source compatibility.

```java
@FunctionalInterface
interface TextFormatter {
    String format(String value);

    default String formatTrimmed(String value) {
        return format(value.trim());
    }

    static TextFormatter identity() {
        return value -> value;
    }

    private static boolean missing(String value) {
        return value == null;
    }
}
```

Only `format` is the functional contract. The other methods can support convenience behavior, factories, or internal implementation without changing the SAM.

Do not use this as a reason to overload a functional interface with unrelated helpers. The interface should still communicate one clear behavior to callers.

## <a id="custom-functional-interface">Defining a Custom Functional Interface</a>

Use a custom functional interface when the domain meaning deserves a name that a general-purpose interface does not express well, or when the contract needs behavior such as a checked exception that standard interfaces do not declare.

```java
@FunctionalInterface
interface DiscountPolicy {
    BigDecimal discountFor(Order order);
}
```

Compared with `Function<Order, BigDecimal>`, `DiscountPolicy` makes the role of the behavior explicit at call sites:

```java
BigDecimal checkout(Order order, DiscountPolicy policy) {
    return order.total().subtract(policy.discountFor(order));
}
```

Prefer a standard interface when its name and shape already describe the behavior clearly. Prefer a domain-specific interface when the domain contract itself is important to readability, documentation, or future evolution.

## <a id="functional-interface-contract-violations">When a Functional Interface Contract Becomes Invalid</a>

The most obvious violation is adding a second unrelated abstract method:

```java
@FunctionalInterface
interface BrokenRule {
    boolean test(String value);
    String describe(); // compile-time error: no longer functional
}
```

Other failures can come from inheritance when inherited abstract declarations cannot be represented by one valid overriding method, for example incompatible return types.

When such a failure appears, fix the interface design instead of removing `@FunctionalInterface` merely to silence the compiler if callers are expected to use lambdas. Common options are:

- keep one abstract behavior and move supporting operations to `default` or `static` methods when that design is coherent;
- split unrelated responsibilities into separate interfaces;
- use an ordinary non-functional interface when the abstraction genuinely requires multiple abstract operations.

The goal is not "make every interface functional." The goal is to use a functional interface only when one behavior contract accurately represents the abstraction.
