# Varargs

Varargs lets callers pass a variable number of arguments without explicitly constructing an array at every call site. Inside the method, however, the parameter still follows an array mental model.

## <a id="varargs-array-model">Varargs Are Arrays</a>

```java
void log(String... messages) {
    System.out.println(messages.length);
}
```

Inside the body, `messages` has type `String[]`.

Callers can write `log("a", "b")` or pass a compatible array. A varargs parameter must be the final parameter.

## <a id="varargs-overload">Varargs and Overloading</a>

Varargs is normally considered after suitable fixed-arity candidates.

Adding a varargs overload can therefore change an overload set in non-obvious ways when combined with widening, boxing, and `null`.

Public overload sets that mix varargs with related reference types deserve representative call-site tests.

## <a id="varargs-generics-warning">Generic Varargs and Heap Pollution</a>

Varargs uses arrays while generic type parameters are erased. Combining the mechanisms can produce heap-pollution warnings for non-reifiable types.

`@SafeVarargs` is a promise that the implementation uses the varargs array safely; it should not be used merely to silence a warning.

The next chapter asks exactly what gets copied when arguments enter a method.
