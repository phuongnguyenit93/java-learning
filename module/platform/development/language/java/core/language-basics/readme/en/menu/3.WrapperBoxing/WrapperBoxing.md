# Wrapper Types, Boxing and Unboxing

## <a id="wrapper-types">Wrapper types and object semantics</a>
Every primitive type has a wrapper class such as `Integer`, `Long`, `Double`, and `Boolean`. Wrappers are immutable objects: they can be `null`, participate in generics and collections, have identity, and provide parsing/conversion helpers.

## <a id="boxing-unboxing">Boxing and unboxing</a>
Autoboxing converts a primitive to its wrapper where the language permits it; unboxing extracts the primitive value. These conversions are inserted by the compiler and can participate in overload resolution.

```java
Integer boxed = 42;
int n = boxed;
```

Convenient syntax does not erase the semantic difference between primitive and object values.

## <a id="wrapper-caching">Wrapper caches and identity pitfalls</a>
Some wrapper factories/autoboxing reuse cached objects for required/common ranges. Two boxed values may therefore sometimes have the same identity and sometimes not. Do not use `==` as numeric-value comparison for wrappers; use `.equals()` or deliberately unbox.

## <a id="unboxing-null">Null unboxing and NullPointerException</a>
Unboxing requires an actual wrapper object. If the reference is `null`, unboxing throws `NullPointerException`. This can happen indirectly in arithmetic, comparison, ternary expressions, or APIs mixing primitive and wrapper values.
