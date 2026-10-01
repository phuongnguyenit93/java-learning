# Variable Capture, Scope, and State

## <a id="lambda-lexical-scope">Lambda Lexical Scope</a>

A lambda is **lexically scoped**: names used inside it are resolved from the surrounding source context in much the same way as names in the enclosing method or block.

```java
class PriceService {
    private final BigDecimal taxRate = new BigDecimal("0.10");

    Function<BigDecimal, BigDecimal> taxedPrice() {
        BigDecimal serviceFee = new BigDecimal("2.00");

        return price -> price
                .add(serviceFee)
                .multiply(BigDecimal.ONE.add(taxRate));
    }
}
```

The lambda body can refer to the local `serviceFee` and the instance field `taxRate` because both names are visible at the lambda's source location.

A lambda does not introduce a separate object-style scope for `this` or `super`. This differs from an anonymous class and becomes important when reading code that accesses enclosing members.

## <a id="captured-local-variables">Captured Local Variables</a>

When a lambda uses a local variable declared outside its body, that local variable is **captured**.

```java
Predicate<String> minimumLength(int min) {
    return text -> text.length() >= min;
}
```

The parameter `min` belongs to the invocation of `minimumLength`, but the returned lambda may be invoked after `minimumLength` has already returned. Java therefore lets the lambda retain the value it needs independently of the original stack frame.

Local variables that are declared inside the lambda are ordinary lambda-local variables and are not captures:

```java
Function<String, Integer> length = text -> {
    String trimmed = text.trim();
    return trimmed.length();
};
```

Here `trimmed` belongs only to the lambda invocation.

## <a id="captured-local-values">What a Lambda Captures from Local Variables</a>

For a captured local variable, the lambda retains the **value of the local binding** needed by the behavior.

For primitives, that value is the primitive value:

```java
int offset = 10;
IntUnaryOperator addOffset = value -> value + offset;
```

For reference variables, the captured value is the object reference:

```java
String prefix = "ID-";
Function<Integer, String> formatter = value -> prefix + value;
```

The lambda does not gain a magic live link to a mutable local slot that can later be reassigned. Java's effectively-final rule prevents such reassignment for captured locals.

This distinction also explains why a captured reference can point to a mutable object: the **reference value** stays fixed even though the object it points to may change.

## <a id="effectively-final">Final and Effectively Final</a>

A local variable, method parameter, or exception parameter captured by a lambda must be `final` or **effectively final**.

An effectively-final variable is not declared with `final`, but its value is assigned once and never reassigned afterward:

```java
int minimum = 3;
Predicate<String> longEnough = text -> text.length() >= minimum;
```

This compiles because `minimum` is never reassigned.

```java
int minimum = 3;
minimum = 5;

// Does not compile because minimum is not effectively final.
Predicate<String> longEnough = text -> text.length() >= minimum;
```

The rule applies to the local **binding**, not to every object reachable through it. Declaring a reference final or effectively final prevents the variable from pointing somewhere else; it does not make the referenced object immutable.

If behavior needs state that changes over time, model that state explicitly rather than trying to work around the capture rule with mutable single-element arrays or similar tricks.

## <a id="instance-static-state-access">Accessing Instance and Static State</a>

The effectively-final restriction is about captured local variables. Instance fields and static fields follow normal Java field-access rules and may be read or changed from a lambda when they are otherwise accessible.

```java
class Counter {
    private int count;
    private static int total;

    Runnable increment = () -> {
        count++;
        total++;
    };
}
```

`count` and `total` are fields, so they are not captured local variables and are not subject to the effectively-final rule.

That does **not** make field mutation automatically safe. If the lambda can run concurrently, normal Java memory-model and synchronization concerns still apply. Lambdas do not add thread safety to mutable state.

## <a id="lambda-this">The Meaning of this inside a Lambda</a>

Inside a lambda, `this` means the same `this` as in the enclosing context. The lambda does not create a new `this` object of its own.

```java
class Greeter {
    private final String prefix = "Hello";

    Runnable greeting(String name) {
        return () -> System.out.println(this.prefix + " " + name);
    }
}
```

Here `this` refers to the `Greeter` instance.

The same lexical behavior applies to unqualified instance-member access and to `super` where `super` is otherwise legal in the enclosing context. This makes lambdas feel like behavior written directly inside the surrounding method rather than like a nested object with separate identity.

## <a id="lambda-vs-anonymous-class-scope">Lambda Scope vs Anonymous-Class Scope</a>

An anonymous class creates a new object scope. Inside its instance methods, `this` refers to the anonymous-class instance. A lambda does not create that kind of scope.

```java
class Demo {
    void showDifference() {
        Runnable anonymous = new Runnable() {
            @Override
            public void run() {
                System.out.println(this.getClass().getName());
            }
        };

        Runnable lambda = () ->
                System.out.println(this.getClass().getName());
    }
}
```

In the anonymous class, `this` is the anonymous `Runnable` object. In the lambda, `this` is the enclosing `Demo` instance.

This is one reason replacing an anonymous class with a lambda is not always a purely textual shortening. Code that relies on anonymous-class identity, its own `this`, or extra members may need a real class or anonymous class instead.

## <a id="captured-reference-mutation">Captured References and Mutable Objects</a>

Effectively final does not imply immutable.

```java
List<String> names = new ArrayList<>();

Consumer<String> addName = name -> names.add(name);
addName.accept("Lan");
addName.accept("Minh");
```

The local variable `names` is never reassigned, so it is effectively final and can be captured. The `ArrayList` itself is still mutable, so the lambda can call `add` on it.

The following is different:

```java
List<String> names = new ArrayList<>();
Consumer<String> addName = name -> names.add(name);

// names = new ArrayList<>(); // would break effectively-final status
```

This distinction is technically legal but also a design warning. Mutation hidden inside callbacks or composed behavior can make execution order and shared state harder to reason about. When state changes are important, make ownership and timing explicit instead of assuming that lambda syntax makes mutation harmless.
