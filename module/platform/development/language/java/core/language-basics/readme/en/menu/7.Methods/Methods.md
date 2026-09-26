# Methods

A method names reusable behavior. A Java invocation is more than “find the same name”: the compiler considers signatures, conversions, and overload-resolution rules.

## <a id="method-signature">Method Signature</a>

For Java overloading, a method signature is primarily its **name + parameter types**. Return type alone cannot distinguish overloads.

Parameters are local variables receiving copied argument values when the method is invoked. `return` completes the method and supplies a result when the return type is not `void`.

```java
public int add(int left, int right) {
    return left + right;
}
```

Read a method as modifiers → return type → name → parameter list → body. Parameter names and return type alone do not create a distinct overload; overload identity is driven by the method name and parameter types.

### A non-void method must not complete normally without a value

For a method whose return type is not `void`, the compiler must prove that the body **cannot finish normally without returning a value**:

```java
int find(boolean found) {
    if (found) {
        return 1;
    }
    // compile error: this path can reach the end without returning an int
}
```

Make every normal path return a value:

```java
int find(boolean found) {
    if (found) {
        return 1;
    }
    return 0;
}
```

A path ending in `throw` needs no following `return` because that path does not complete normally:

```java
int requireValue(boolean valid) {
    if (!valid) {
        throw new IllegalStateException("invalid");
    }
    return 1;
}
```

This is another form of **compile-time control-flow analysis**, closely related to definite assignment: the compiler reasons about paths rather than guessing runtime intent.

## <a id="method-invocation-conversion">Method Invocation Conversions</a>

An argument may use conversions permitted in method-invocation context, including identity conversion, primitive/reference widening, boxing/unboxing in applicable phases, and eventually varargs conversion.

Not every explicit cast that is legal in source is automatically performed by overload resolution.

```java
void use(long value) { }
use(10); // int widens to long

void useByte(byte value) { }
int x = 1;
// useByte(x); // no implicit narrowing
useByte((byte) x);
```

## <a id="overload-resolution-phases">Overload Resolution Phases</a>

A useful mental model is:

```text
1. fixed arity without boxing/varargs fallback
        ↓ if needed
2. applicable boxing/unboxing conversions
        ↓ if still needed
3. varargs fallback
```

The language specification is more detailed, but this ordering explains many “widening vs boxing vs varargs” questions.

```java
void pick(long x) { }
void pick(Integer x) { }
void pick(int... x) { }

pick(1); // long overload wins before boxing/varargs fallback
```

## <a id="most-specific-overload">Most-specific Overload</a>

If several candidates are applicable, the compiler tries to choose the **most specific** according to type rules.

```java
void print(Object x) { }
void print(String x) { }

print("java"); // String overload
```

This is a compile-time type decision, not a choice based on source declaration order.

If applicable overloads are unrelated and none is more specific, the call is ambiguous rather than chosen arbitrarily.

## <a id="null-overload-ambiguity">null Overload Ambiguity</a>

`null` is compatible with reference types. If unrelated overloads are equally applicable:

```java
void print(String x) { }
void print(Integer x) { }

print(null); // ambiguous
```

the compiler cannot choose one candidate. An explicit cast can disambiguate when that is the actual intent.

`null` is not always ambiguous. If one overload parameter type is more specific than another, that overload can still win:

```java
void print(Object x) { }
void print(String x) { }
print(null); // String overload
```

## <a id="method-call-evaluation">Argument Evaluation Order</a>

Java evaluates argument expressions left to right before entering the method body.

Even with defined order, side-effect-heavy argument lists are hard to read. Extract meaningful intermediate calculations when order matters.

The receiver and argument expressions are evaluated before entering the method body. The callee receives their resulting **values**, not the caller's variable slots. That leads directly to Java's pass-by-value model.

## <a id="recursion-stack">Recursion and the Call Stack</a>

Recursive calls create additional stack frames. Missing base cases or excessive depth can result in `StackOverflowError`.

Recursion is natural for some tree/divide-and-conquer algorithms; iterative solutions can be simpler for long linear repetition.

```java
int factorial(int n) {
    if (n <= 1) return 1;
    return n * factorial(n - 1);
}
```

Each recursive call owns a separate stack frame. Java does not guarantee tail-call optimization, so recursion depth remains a real runtime constraint.

The next chapter introduces varargs, Java's syntax for variable argument counts.
