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

Calls such as `log()`, `log("a")`, and `log("a", "b")` are modeled inside the method as arrays of length 0, 1, and 2. A caller may also pass an existing compatible array.

Varargs is therefore call-site convenience built on array semantics, including allocation and mutability considerations.

`log((String[]) null)` is different from `log()`: the former passes a null array reference, while the latter receives an empty array. Public APIs should define whether null arrays are valid.

## <a id="varargs-overload">Varargs and Overloading</a>

Varargs is normally considered after suitable fixed-arity candidates.

Adding a varargs overload can therefore change an overload set in non-obvious ways when combined with widening, boxing, and `null`.

Public overload sets that mix varargs with related reference types deserve representative call-site tests.

Fixed arity normally wins first:

```java
void send(String value) { }
void send(String... values) { }
send("one"); // fixed-arity overload
```

`null`, boxing, and related reference types can make overload sets surprisingly ambiguous, so convenience overloads should not obscure call semantics.

## <a id="varargs-generics-warning">Generic Varargs and Heap Pollution</a>

Varargs uses arrays while generic type parameters are erased. Combining the mechanisms can produce heap-pollution warnings for non-reifiable types.

`@SafeVarargs` is a promise that the implementation uses the varargs array safely; it should not be used merely to silence a warning.

Arrays carry runtime component-type behavior while generic parameters are erased. A generic varargs array therefore cannot fully represent parameterization such as `List<String>[]` at runtime, and unsafe writes/aliases can produce heap pollution.

Practical rules: prefer collections when the variable-size input is a real data structure; use varargs for clear call-site ergonomics; do not perform unsafe writes or expose a generic varargs array to code that can corrupt it; use `@SafeVarargs` only when the implementation is actually safe.

The next chapter asks exactly what gets copied when arguments enter a method.
