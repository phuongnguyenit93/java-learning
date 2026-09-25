# Methods in Java

## <a id="method-signature">Method signature, parameters and return</a>
A Java method declares a name and parameter types; the return type is not part of overload identity. Parameters are local variables initialized from argument values. Method contracts should make valid input, output, side effects, and exceptional behavior clear.

## <a id="method-invocation-conversion">Method invocation conversions</a>
When checking whether a method can accept an argument, Java can use identity conversion, primitive/reference widening, boxing/unboxing combinations allowed by invocation rules, and finally varargs applicability. Not every mathematically possible conversion is considered.

## <a id="overload-resolution-phases">Overload resolution phases</a>
Overload resolution is compile-time selection. The compiler first looks for applicable fixed-arity methods in earlier phases and only later considers varargs. An earlier applicable phase wins before a later one is considered.

```java
void f(long x) {}
void f(Integer x) {}
void f(int... x) {}
// f(1) selects f(long), not boxing or varargs.
```

## <a id="most-specific-overload">Selecting the most-specific applicable overload</a>
If several overloads are applicable in the same phase, Java chooses the most specific according to type relationships and invocation compatibility. It is not simply the smallest numeric type or the method written first.

## <a id="null-overload-ambiguity">null arguments and ambiguous unrelated reference overloads</a>
`null` is compatible with reference types. If overloads take unrelated reference types, `f(null)` can be ambiguous because neither candidate is more specific. A cast can disambiguate, but a clearer API often avoids such overload sets.

## <a id="method-call-evaluation">Argument evaluation order</a>
Arguments are evaluated left-to-right before the method body starts. Side effects therefore have a defined order, but dense side-effect expressions still harm readability.

## <a id="recursion-stack">Recursion and call-stack cost</a>
Each recursive call creates another invocation frame until the base case returns. Java does not guarantee tail-call elimination, so deep recursion can cause `StackOverflowError`. Iteration may be safer for unbounded depth.
