# Methods

A method names reusable behavior. A Java invocation is more than “find the same name”: the compiler considers signatures, conversions, and overload-resolution rules.

## <a id="method-signature">Method Signature</a>

For Java overloading, a method signature is primarily its **name + parameter types**. Return type alone cannot distinguish overloads.

Parameters are local variables receiving copied argument values when the method is invoked. `return` completes the method and supplies a result when the return type is not `void`.

## <a id="method-invocation-conversion">Method Invocation Conversions</a>

An argument may use conversions permitted in method-invocation context, including identity conversion, primitive/reference widening, boxing/unboxing in applicable phases, and eventually varargs conversion.

Not every explicit cast that is legal in source is automatically performed by overload resolution.

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

## <a id="most-specific-overload">Most-specific Overload</a>

If several candidates are applicable, the compiler tries to choose the **most specific** according to type rules.

```java
void print(Object x) { }
void print(String x) { }

print("java"); // String overload
```

This is a compile-time type decision, not a choice based on source declaration order.

## <a id="null-overload-ambiguity">null Overload Ambiguity</a>

`null` is compatible with reference types. If unrelated overloads are equally applicable:

```java
void print(String x) { }
void print(Integer x) { }

print(null); // ambiguous
```

the compiler cannot choose one candidate. An explicit cast can disambiguate when that is the actual intent.

## <a id="method-call-evaluation">Argument Evaluation Order</a>

Java evaluates argument expressions left to right before entering the method body.

Even with defined order, side-effect-heavy argument lists are hard to read. Extract meaningful intermediate calculations when order matters.

## <a id="recursion-stack">Recursion and the Call Stack</a>

Recursive calls create additional stack frames. Missing base cases or excessive depth can result in `StackOverflowError`.

Recursion is natural for some tree/divide-and-conquer algorithms; iterative solutions can be simpler for long linear repetition.

The next chapter introduces varargs, Java's syntax for variable argument counts.
