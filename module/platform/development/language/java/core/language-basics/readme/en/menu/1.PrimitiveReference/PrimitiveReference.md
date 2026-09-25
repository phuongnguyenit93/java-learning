# Primitive and Reference Types

## <a id="primitive-vs-reference-model">Primitive vs reference value model</a>
Java variables hold values. A primitive variable directly holds a primitive value such as `int`, `double`, `char`, or `boolean`. A reference variable holds a reference value that can identify an object or array, or it can hold `null`.

Copying a primitive copies the primitive value. Copying a reference copies the reference value, so two variables can refer to the same mutable object. This distinction explains pass-by-value, aliasing, identity, defensive copying, and nullability.

```java
int a = 10;
int b = a;          // independent primitive value
List<String> x = new ArrayList<>();
List<String> y = x; // copied reference; one shared object
```

## <a id="primitive-ranges-and-defaults">Primitive ranges, literals and defaults</a>
Java has eight primitive types. Integer primitives have fixed widths; floating-point primitives follow IEEE 754; `char` is an unsigned UTF-16 code unit; `boolean` represents logical truth values. Fields receive language-defined default values, but local variables must be definitely assigned before they are read.

Numeric literals participate in compile-time typing. Suffixes such as `L`, `F`, and `D`, radix prefixes, underscores, and narrowing constant expressions can affect whether an assignment compiles.

## <a id="reference-value-semantics">What a reference value actually stores</a>
The Java language does not expose a physical address as the semantic value of an object reference. The useful model is that a reference can identify an object, be copied, compared for identity with `==`, dereferenced, or be `null`.

An object can have many aliases and its lifetime is not tied to one variable. Losing one reference does not destroy the object while other reachable references still exist. Reachability and garbage collection belong to the JVM model; ordinary Java code should reason in terms of references and identity rather than addresses.
